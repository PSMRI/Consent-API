/*
 * AMRIT – Accessible Medical Records via Integrated Technology
 * Integrated EHR (Electronic Health Records) Solution
 *
 * Copyright (C) "Piramal Swasthya Management and Research Institute"
 *
 * This file is part of AMRIT.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/.
 */

package com.iemr.consent.service.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import com.iemr.consent.support.TestFixtures;

class HealthServiceImplTest {

	private static final long TIMEOUT_MS = 300;

	private DataSource dataSource;

	private Connection connection;

	private Statement statement;

	private RedisConnectionFactory redisFactory;

	private RedisConnection redisConnection;

	private ExecutorService executor;

	private HealthServiceImpl service;

	@BeforeEach
	void setUp() throws Exception {
		dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		statement = mock(Statement.class);
		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.createStatement()).thenReturn(statement);
		redisFactory = mock(RedisConnectionFactory.class);
		redisConnection = mock(RedisConnection.class);
		when(redisFactory.getConnection()).thenReturn(redisConnection);
		when(redisConnection.ping()).thenReturn("PONG");
		executor = Executors.newFixedThreadPool(4);
		service = new HealthServiceImpl(dataSource, redisFactory, TestFixtures.CLOCK, TIMEOUT_MS, executor);
	}

	@AfterEach
	void tearDown() {
		service.shutdown();
	}

	private static String status(HealthStatus health, String component) {
		return health.components().get(component).status();
	}

	@Test
	void bothUp() throws Exception {
		HealthStatus health = service.check();

		assertThat(health.status()).isEqualTo("UP");
		assertThat(health.isUp()).isTrue();
		assertThat(health.checkedAt()).isEqualTo("2026-10-29T10:00:00Z");
		assertThat(health.components()).containsOnlyKeys("database", "cache");
		assertThat(status(health, "database")).isEqualTo("UP");
		assertThat(status(health, "cache")).isEqualTo("UP");
		assertThat(health.downComponents()).isEmpty();
		verify(statement).setQueryTimeout(1);
		verify(statement).execute("SELECT 1");
	}

	@Test
	void databaseDown() throws Exception {
		when(dataSource.getConnection()).thenThrow(new SQLException("jdbc:mysql://db-host:3306 refused"));

		HealthStatus health = service.check();

		assertThat(health.status()).isEqualTo("DOWN");
		assertThat(status(health, "database")).isEqualTo("DOWN");
		assertThat(status(health, "cache")).isEqualTo("UP");
		assertThat(health.downComponents()).isEqualTo("database");
		assertThat(health.toString()).doesNotContain("db-host").doesNotContain("3306");
	}

	@Test
	void cacheDownOrNotPong() {
		when(redisFactory.getConnection()).thenThrow(new RedisConnectionFailureException("redis-host:6379"));
		HealthStatus health = service.check();
		assertThat(status(health, "cache")).isEqualTo("DOWN");
		assertThat(health.downComponents()).isEqualTo("cache");
		assertThat(health.toString()).doesNotContain("redis-host");
	}

	@Test
	void unexpectedPingReplyIsDown() {
		when(redisConnection.ping()).thenReturn(null);
		assertThat(status(service.check(), "cache")).isEqualTo("DOWN");
	}

	@Test
	void bothDown() throws Exception {
		when(dataSource.getConnection()).thenThrow(new SQLException("x"));
		when(redisFactory.getConnection()).thenThrow(new RedisConnectionFailureException("y"));

		assertThat(service.check().downComponents()).isEqualTo("database, cache");
	}

	@Test
	void hungDatabaseIsDownWithinTheTimeoutAndChecksRunInParallel() throws Exception {
		CountDownLatch bothEntered = new CountDownLatch(2);
		CountDownLatch release = new CountDownLatch(1);
		when(dataSource.getConnection()).thenAnswer(invocation -> {
			bothEntered.countDown();
			release.await(10, TimeUnit.SECONDS);
			return connection;
		});
		when(redisConnection.ping()).thenAnswer(invocation -> {
			bothEntered.countDown();
			assertThat(bothEntered.await(5, TimeUnit.SECONDS)).isTrue();
			return "PONG";
		});

		long started = System.nanoTime();
		HealthStatus health = service.check();
		Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

		assertThat(bothEntered.getCount()).isZero();
		assertThat(status(health, "database")).isEqualTo("DOWN");
		assertThat(status(health, "cache")).isEqualTo("UP");
		assertThat(elapsed).isLessThan(Duration.ofSeconds(2));

		HealthStatus second = service.check();
		assertThat(status(second, "cache")).isEqualTo("UP");
		release.countDown();
	}

	@Test
	void rejectedOrInterruptedChecksAreDown() throws Exception {
		ExecutorService rejecting = mock(ExecutorService.class);
		when(rejecting.invokeAll(any(), anyLong(), any())).thenThrow(new RejectedExecutionException("full"));
		HealthServiceImpl rejected = new HealthServiceImpl(dataSource, redisFactory, TestFixtures.CLOCK, TIMEOUT_MS,
				rejecting);
		assertThat(rejected.check().downComponents()).isEqualTo("database, cache");

		ExecutorService interrupting = mock(ExecutorService.class);
		when(interrupting.invokeAll(any(), anyLong(), any())).thenThrow(new InterruptedException());
		HealthServiceImpl interrupted = new HealthServiceImpl(dataSource, redisFactory, TestFixtures.CLOCK,
				TIMEOUT_MS, interrupting);
		assertThat(interrupted.check().status()).isEqualTo("DOWN");
		assertThat(Thread.interrupted()).isTrue();
	}

	@Test
	void interruptedWhileReadingAResultIsDown() throws Exception {
		ExecutorService pool = mock(ExecutorService.class);
		@SuppressWarnings("unchecked")
		java.util.concurrent.Future<Boolean> interrupted = mock(java.util.concurrent.Future.class);
		when(interrupted.get()).thenThrow(new InterruptedException());
		@SuppressWarnings("unchecked")
		java.util.concurrent.Future<Boolean> up = mock(java.util.concurrent.Future.class);
		when(up.get()).thenReturn(Boolean.TRUE);
		org.mockito.Mockito.doReturn(java.util.List.of(interrupted, up)).when(pool).invokeAll(any(), anyLong(), any());

		HealthStatus health = new HealthServiceImpl(dataSource, redisFactory, TestFixtures.CLOCK, TIMEOUT_MS, pool)
				.check();

		assertThat(health.downComponents()).isEqualTo("database");
	}

	@Test
	void springConstructorUsesItsOwnBoundedPool() {
		HealthServiceImpl springService = new HealthServiceImpl(dataSource, redisFactory, TestFixtures.CLOCK,
				TestFixtures.consentProperties());
		try {
			assertThat(springService.check().isUp()).isTrue();
		} finally {
			springService.shutdown();
		}
	}
}
