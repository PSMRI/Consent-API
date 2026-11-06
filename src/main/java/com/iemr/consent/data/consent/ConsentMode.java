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

package com.iemr.consent.data.consent;

/**
 * How consent was taken (proposal §3.1 rule 6). {@code PAPER} is for legacy records only (feature 008): it has no
 * {@code ModeProofRule}, so a save (API 7) refuses it, as it does {@code VOICE_CLIP}.
 */
public enum ConsentMode {
	SELF_READ, READ_ALOUD, VOICE_CLIP, WITNESSED, SIGNATURE_PHOTO, OTP, PAPER
}
