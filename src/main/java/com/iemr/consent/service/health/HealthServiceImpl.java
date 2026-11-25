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

import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import com.iemr.consent.config.ConsentProperties;
import com.iemr.consent.service.health.HealthStatus.ComponentStatus;

import jakarta.annotation.PreDestroy;

/**
 * Runs {@code SELECT 1} on the {@code db_identity} datasource and a Redis {@code PING} in
 * parallel, each bounded by {@code consent.health.check-timeout-ms}, so {@code /health} answers
 * within 2 s even when a dependency hangs (SC-006, research R7).
 *
 * <p>Mirrors Common-API's {@code service/health/HealthService}, without its extra diagnostics.
 * Checks run on a small private pool with no queue: a check still running after the timeout is
 * cancelled, and if the pool is exhausted (several hung checks) the components are reported
 * {@code DOWN} instead of waiting. Any exception or timeout is {@code DOWN}; no exception text is
 * kept.</p>
 */
@Service
public class HealthServiceImpl implements HealthService {

	private static final int QUERY_TIMEOUT_SECONDS = 1;

	private final DataSource dataSource;

	private final RedisConnectionFactory redisConnectionFactory;

	private final Clock clock;

	private final long timeoutMs;

	private final ExecutorService executor;

	@Autowired
	public HealthServiceImpl(DataSource dataSource, RedisConnectionFactory redisConnectionFactory, Clock clock,
			ConsentProperties properties) {
		this(dataSource, redisConnectionFactory, clock, properties.health().checkTimeoutMs(), newExecutor());
	}

	private static ExecutorService newExecutor() {
		return new ThreadPoolExecutor(4, 8, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), runnable -> {
			Thread thread = new Thread(runnable, "health-check");
			thread.setDaemon(true);
			return thread;
		}, new ThreadPoolExecutor.AbortPolicy());
	}

	HealthServiceImpl(DataSource dataSource, RedisConnectionFactory redisConnectionFactory, Clock clock,
			long timeoutMs, ExecutorService executor) {
		this.dataSource = dataSource;
		this.redisConnectionFactory = redisConnectionFactory;
		this.clock = clock;
		this.timeoutMs = timeoutMs;
		this.executor = executor;
	}

	@Override
	public HealthStatus check() {
		List<Callable<Boolean>> checks = List.of(this::databaseUp, this::cacheUp);
		List<Boolean> results = run(checks);
		Map<String, ComponentStatus> components = new LinkedHashMap<>();
		components.put("database", component(results.get(0)));
		components.put("cache", component(results.get(1)));
		boolean allUp = results.stream().allMatch(Boolean.TRUE::equals);
		return new HealthStatus(allUp ? HealthStatus.UP : HealthStatus.DOWN, clock.instant().toString(),
				components);
	}

	@PreDestroy
	public void shutdown() {
		executor.shutdownNow();
	}

	private List<Boolean> run(List<Callable<Boolean>> checks) {
		try {
			return executor.invokeAll(checks, timeoutMs, TimeUnit.MILLISECONDS).stream()
					.map(HealthServiceImpl::outcome).toList();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return checks.stream().map(check -> Boolean.FALSE).toList();
		} catch (RejectedExecutionException e) {
			return checks.stream().map(check -> Boolean.FALSE).toList();
		}
	}

	private static Boolean outcome(Future<Boolean> future) {
		if (future.isCancelled()) {
			return Boolean.FALSE;
		}
		try {
			return future.get();
		} catch (ExecutionException | InterruptedException e) {
			return Boolean.FALSE;
		}
	}

	private static ComponentStatus component(Boolean up) {
		return new ComponentStatus(Boolean.TRUE.equals(up) ? HealthStatus.UP : HealthStatus.DOWN);
	}

	private Boolean databaseUp() throws Exception {
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
			statement.execute("SELECT 1");
			return Boolean.TRUE;
		}
	}

	private Boolean cacheUp() {
		try (RedisConnection connection = redisConnectionFactory.getConnection()) {
			return "PONG".equalsIgnoreCase(connection.ping());
		}
	}
}
