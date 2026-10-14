# AMRIT Consent Manager — DPDP Act, 2023 (Approved Proposal)

Source of requirements for Consent-API (see constitution Principle X). Scenario IDs
(C, H, M, T, E, A, N) and decision IDs (D-xx) are referenced by every spec.

## 1. Problem Statement

AMRIT has no real proof of patient consent today. Every patient is saved as having agreed,
whatever they did. That does not meet the DPDP Act, 2023 in any of the six services this
document covers: HWC, MMU, TM, ECD, 104 and 1097.

## 2. Existing System vs Why it is a DPDP Problem

| Existing system | Why it is a DPDP problem |
|---|---|
| Consent is always saved as "Yes" | No proof of consent (Act §6(10)) |
| Closing the pop-up counts as agreeing; there is no Decline button | Consent must be a clear affirmative action and refusable (§6(1)); patients can't refuse |
| Sharing with spouse, family and doctor is switched on by default | Pre-ticked consent is not valid |
| Notice is in only 2–5 languages and does not list data, purposes or rights | Notice must be itemised, in English and the 22 Eighth Schedule languages (§5, Rule 3) |
| Helpline-104 consent is a fixed English script and the answer is not saved | No evidence for helpline callers |
| No record of who viewed a patient's data | Access logs are required (Rule 6) |
| No guardian consent for children | Verifiable parental consent is required (Rule 10) |
| Patient personal details are not encrypted in the database | Reasonable security safeguards are required (Rule 6) |

## 3. Solution Overview

One central Consent Manager serves all six services. Every service uses the same consent
screen and the same consent rules. No patient data is collected or opened until Consent-API
confirms it is allowed.

What the patient gets:

- A clear notice in their own language, read on screen or played aloud, before any data is collected.
- A separate yes/no for each use of their data. All switches start off, and Decline is as easy as Accept.
- Time-limited access for a doctor, approved by OTP, which ends on its own.
- Withdrawal at any time, through any channel, which takes effect at once.
- A tamper-proof record of every decision.

The front end only captures and shows consent. The back end decides, records and enforces
it. The database keeps an unchangeable history of every decision.

### 3.1 Front End

Four UI pieces, all built once and reused by every service. The consent screen replaces
today's pop-up.

| Component | Where | What it does |
|---|---|---|
| Consent screen | Common-UI (Angular), embedded in HWC, MMU and TM registration and revisit | Language choice, notice (text or audio), one switch per purpose, evidence capture, guardian step, Accept / Decline |
| Helpline consent panel | 104, 1097 and ECD agent screens | Script in the caller's language, yes/no per purpose, call ID linked automatically |
| Preference centre | Patient record in every service | Shows current consents, change a purpose, withdraw, cancel a doctor's access, view history |
| Admin screens | ADMIN-UI | Purposes, notice versions, translations, audio, approvals; review queues for withdrawals and emergency access |

Consent screen rules:

1. Patient picks a language first.
2. The notice lists each data item, why it is used, how long it is kept, who it is shared
   with, how to withdraw, and how to complain (to the DPO or the Data Protection Board).
3. Accept stays locked until the notice has been scrolled to the end or the audio has
   played to the end.
4. Mandatory purposes (e.g., registration, treatment) are shown first; optional ones (SMS
   reminders, sharing with family or other facilities, research) are separate switches.
   All switches start off.
5. Decline is as visible as Accept. Closing the screen = no consent; nothing is saved as "Yes".
6. The worker picks how consent was taken, and the screen asks for the matching proof:

| Mode | When | Proof saved |
|---|---|---|
| Self-read | Patient can read | Notice version, language, text hash, time, device |
| Read aloud | Patient cannot read | Health worker ID + confirmation tick, audio played to end |
| Voice clip | Low literacy, extra proof | 30–60 s recording + hash |
| Witnessed | No phone, extra safety | Witness name and relationship |
| Signature / thumb + photo | Facility or van | Images + hash |
| OTP | Patient or guardian has a phone and network | OTP verified time, masked number |
| Verbal on call | 104, 1097, ECD | Call ID + recording link |

7. If the patient is under 18, or needs a guardian (disability), the screen switches to the
   guardian step: guardian name, relationship, phone → OTP to the guardian's phone →
   guardian gives consent.
8. If a mandatory purpose is declined, the screen explains that the service cannot
   continue and stops data entry.

### 3.2 Back End

Consent-API is a new Spring Boot 3.2 / Java 17 service, built the same way as Common-API
and Identity-API. It is the only place that stores or decides consent. It uses the same
JWT login and returns the standard AMRIT OutputResponse.

#### 3.2.1 Consent-API modules

| Module | Responsibility |
|---|---|
| Purposes | List of data uses (registration, treatment, reminders, sharing, research…), legal basis, mandatory or optional, validity period |
| Notices | Versioned notice per language, audio, reviewer sign-off |
| Capture | Saves yes/no per purpose with mode, proof, worker, device, time |
| Consent check | Answers "is this allowed?" for every other service in milliseconds |
| Withdrawal | Takes back consent per purpose or all; triggers enforcement |
| Access grants | Doctor requests time-limited access; patient approves by OTP; auto-expiry; emergency (break-glass) access with reason, 24 h |
| Evidence | Stores voice clips, signatures, photos in encrypted file storage, linked by ID and hash |
| Rights & grievances | Requests to see, correct or delete data, and complaints, with reference number and escalation to the DPO |
| Audit log | Every action recorded in a tamper-proof (hash-chained) log |
| Sync | Receives offline consents from MMU vans; removes duplicates; resolves conflicts |
| Jobs (Quartz) | Expire consents and access, renewal reminders, guardian-to-adult switch at 18, retention clean-up, audit-log check |

#### 3.2.2 Main APIs

| Function | Endpoint | Used by |
|---|---|---|
| Get notice | `GET /consent/v1/notices/active?context&lang` | All UIs |
| Save consent | `POST /consent/v1/consents` | Consent screen, helpline panel, van sync |
| Get patient's consents | `GET /consent/v1/principals/{benRegId}/consents` | Preference centre |
| Pending actions | `GET /consent/v1/principals/{benRegId}/pending-actions` | Registration / revisit (consent missing, re-consent due) |
| Change preferences | `POST /consent/v1/principals/{benRegId}/preferences` | Preference centre |
| Withdraw | `POST /consent/v1/principals/{benRegId}/withdrawals` | Preference centre, helpline panel |
| Check consent | `POST /consent/v1/check` | Consent-check library in every service |
| Send / verify OTP | `POST /consent/v1/otp/challenges`, `…/{id}/verify` | Guardian, doctor access |
| Doctor access | `POST /consent/v1/access-grants`, `…/{id}/approve`, `…/{id}/revoke` | TM, HWC referrals |
| Emergency access | `POST /consent/v1/access-grants/break-glass` | Doctors only |
| Upload evidence | `POST /consent/v1/evidence` | Consent screen, apps |
| Van sync | `GET /consent/v1/sync/snapshot`, `POST /consent/v1/sync/upload` | MMU van server |
| Rights / grievance | `POST /consent/v1/rights-requests` | Any service, helplines |

## 4. How other services enforce consent

- Each HWC, MMU, TM, ECD, 104 and 1097 API that reads or writes patient data gets one
  annotation: `@RequiresConsent(purpose = "P-SHR")`.
- The shared check library looks in Redis first and asks Consent-API only if the answer is
  not cached.
- If not allowed, the API returns 403 with a reason: CONSENT_NOT_FOUND, CONSENT_DENIED,
  CONSENT_WITHDRAWN, CONSENT_EXPIRED, RECONSENT_REQUIRED, GUARDIAN_REQUIRED.
  (Implemented per constitution Principle III as OutputResponse `statusCode = 5403` +
  `reasonCode`.)
- Every allowed or denied access is written to the data-access log (who, which patient,
  what action, when).
- On any consent change, Consent-API clears the cached answer on all servers, so the next
  check reflects it at once.
- SMS reminder jobs call the check before every send, so they stop the moment consent is
  withdrawn.

## 5. Database

New `cm_` tables in the existing `db_identity` database. Consent rows are never edited or
deleted; every change adds a new version, so the full history is always available as proof.

| Table | Purpose | Key columns |
|---|---|---|
| cm_purpose | Master list of data uses | purpose_code, version, name, legal_basis, is_mandatory, is_withdrawable, validity_days |
| cm_notice | Notice versions | notice_code, version_no, context (REGISTRATION / HELPLINE …), is_material, status, approved_by |
| cm_notice_translation | Notice text per language | notice_version_id, language_code, body, text_hash, audio_ref, helpline_script |
| cm_consent | One row per consent event | consent_id (UUID), beneficiary_reg_id, event_type (NEW / UPDATE / RENEWAL / WITHDRAWAL), consenting_party (SELF / GUARDIAN), notice_version_id, language, channel, mode, captured_by, facility, service_line, device_id, call_id, captured_at, sync_status |
| cm_consent_purpose | Decision per purpose | consent_id, beneficiary_reg_id, purpose_code, decision, status (GRANTED / DENIED / WITHDRAWN / EXPIRED / RECONSENT_REQUIRED / SUPERSEDED), version_no, is_current, valid_until |
| cm_consent_evidence | Proof files and details | evidence_id, consent_id, type, file_ref, sha256, witness_name (encrypted), otp_status, call_id |
| cm_guardian_link | Guardian for a child or person with disability | beneficiary_reg_id, guardian name/phone (encrypted), relationship, verification_method, verified_at, valid_until (18th birthday) |
| cm_access_grant | Time-limited doctor access and emergency access | beneficiary_reg_id, grant_type (CONSENTED / BREAK_GLASS), grantee, purposes, reason, status, valid_until, reviewed_by |
| cm_rights_request | Data requests and grievances | reference_no, type (ACCESS / CORRECTION / ERASURE / GRIEVANCE), status, sla_due_at, assigned_to |
| cm_propagation_outbox | Events sent to partners receiving data | event_type, processor, status, acked_at |
| cm_audit_log | Tamper-proof log of every consent action | action, beneficiary_reg_id, purpose, status, initiator, source_ip, prev_hash, entry_hash |
| cm_data_access_log | Who viewed or changed which patient record | user_id, beneficiary_reg_id, service, action, purpose, decision, time (kept ≥ 1 year) |

Rules:

- Only one current decision per patient per purpose (unique index on is_current).
- Name, phone, address, ID numbers and guardian/witness details are encrypted at field
  level; phone and ID are masked on screen.
- The application DB user cannot UPDATE or DELETE the audit log.
- Evidence files live in encrypted file storage, not in the database; the row stores the
  link and hash.
- Retention periods per data type are a Legal decision (§13).

## 6. Common Scenarios (all services)

These apply in HWC, MMU, TM, ECD, 104 and 1097 alike.

| # | Scenario | What happens | System result |
|---|---|---|---|
| C1 | New patient, agrees to all | Language → notice → switches on → proof → Accept | All purposes GRANTED; SMS confirmation; data entry opens |
| C2 | Partial consent | Agrees to mandatory, declines optional (e.g., SMS reminders, research) | Declined purposes saved as DENIED; reminders blocked for that patient |
| C3 | Refuses a mandatory purpose / presses Decline | Screen explains service cannot continue | Saved as DENIED; no data collected; record shows "Consent declined" |
| C4 | Closes the screen without deciding | Nothing saved as consent | Data entry stays blocked; "Consent pending" shown next time |
| C5 | Existing patient, consent taken before DPDP | Marked LEGACY | Fresh consent asked at next visit or call; optional uses blocked until then |
| C6 | Child under 18 | Guardian step; OTP to guardian's phone | Consent saved with consenting_party = GUARDIAN and guardian link |
| C7 | Guardian has no phone / OTP fails | Fallback: ID seen by staff, or witness + signature (D-03) | Guardian saved as "ID sighted" / "Unverified"; flagged for follow-up |
| C8 | Person with disability | Lawful guardian gives consent, same as C6 | Guardian type = disability guardian |
| C9 | Child turns 18 | Daily job ends guardian link | Status RECONSENT_REQUIRED; patient asked for own consent at next contact |
| C10 | Patient changes one preference | Preference centre → switch → confirm | New version for that purpose only; others unchanged |
| C11 | Withdraws one purpose or all | Preference centre or call → implications shown → confirm | WITHDRAWN at once; cache cleared; SMS confirmation; reminders, sharing and doctor access for that purpose stop; partners notified |
| C12 | Withdraws a purpose the law requires (e.g., legal reporting) | System explains why it can't be withdrawn | Not withdrawn; patient can raise a grievance |
| C13 | Data after withdrawal | Not auto-deleted | Reviewer decides keep (with legal reason), anonymise or delete |
| C14 | Notice changes materially (new purpose or recipient) | Admin publishes new version | Affected consents RECONSENT_REQUIRED; re-asked at next contact; grace period (D-05) |
| C15 | Notice small correction (typo) | New version, not material | No re-consent |
| C16 | Consent nearing expiry | Reminder before end date | Renewed → new version; not renewed → EXPIRED, check says no |
| C17 | Patient changes language | Notice reloaded in new language | Consent recorded in the language actually shown |
| C18 | Double tap / retry | Same request key | Only one record; no duplicate SMS |
| C19 | Consent-API down | Check uses cache; otherwise rule D-04 | Optional uses blocked; treatment at the treating facility allowed and flagged |
| C20 | Same patient updated from two places at once | Second save rejected as stale | User reloads and confirms |
| C21 | Patient asks to see, correct or delete data | Rights request with reference number | Verified by OTP; handled by grievance officer; delete checked against legal retention |
| C22 | Complaint | Grievance logged | Reference number SMS; escalates to DPO if not resolved in time |
| C23 | Emergency, patient can't consent | Doctor uses break-glass with reason | Access 24 h; DPO alerted; reviewed later |
| C24 | Audit / regulator request | DPO exports records | Export includes consent history, proof details and audit-log integrity check |

## 7. HWC (Health & Wellness Centre)

Face-to-face, online at the facility; consent is taken at the registration desk and checked
on every clinical screen.

| # | Scenario | Flow | System result |
|---|---|---|---|
| H1 | New patient registration | Registrar opens consent screen before personal details; patient self-reads | Consent saved; registration form unlocks |
| H2 | Patient cannot read | Registrar/nurse plays audio or reads aloud; optional voice clip; ticks confirmation | Mode = read aloud; worker ID saved |
| H3 | Revisit, consent valid | "Pending actions" returns nothing | Visit proceeds; no consent screen |
| H4 | Revisit, consent missing / legacy / re-consent due | Banner "Consent pending" opens the consent screen | Must be completed before optional uses; treatment per D-02 |
| H5 | Referral to another facility | Doctor refers; receiving facility needs history | Allowed only if P-SHR (share with other facilities) is GRANTED; otherwise receiving facility sees 403 and requests time-limited access (H6) |
| H6 | Doctor at another facility needs the record | Access request → OTP to patient → approve for N days | Access ACTIVE until end date, then stops automatically |
| H7 | Sharing with spouse/family | Separate switch, off by default | Family access only if P-FAM GRANTED |
| H8 | SMS follow-up / medicine reminders | Reminder job checks P-REM before each SMS | Sent only if GRANTED; stops at once on withdrawal |
| H9 | Child brought by parent | Age < 18 from DOB → guardian step | Parent consents; OTP to parent's phone |
| H10 | Patient withdraws at the desk | Staff opens preference centre, switches off, confirms | WITHDRAWN; SMS confirmation; other facilities lose access |
| H11 | Registrar views/prints a record | Check + data-access log | Every view, edit, print logged with user and time |
| H12 | Emergency at HWC, patient unconscious | Doctor uses break-glass | 24 h access, reason saved, DPO review |

## 8. MMU (Mobile Medical Unit)

Vans run their own local AMRIT server and database and often have no internet; consent
works fully offline and syncs later.

| # | Scenario | Flow | System result |
|---|---|---|---|
| M1 | Before the trip | Van downloads latest notices (all languages), purposes, and consent status of patients in its area | Van can show notices and check consent offline |
| M2 | New patient, offline | Notice from local storage; consent saved locally with a device-generated ID; signed with the van's key | Local check allows care; status "Not synced" |
| M3 | Low-literacy patient in van | Audio notice + voice clip, or witness, or signature/thumb + photo | Proof files stored encrypted on the van; uploaded at sync |
| M4 | Consent check in van | Van services check against the local database, including expiry using the van clock | Same rules as central |
| M5 | Withdrawal in van | Staff withdraws in preference centre | Effective in the van immediately; sent to central at next sync |
| M6 | Back online | Consents, withdrawals, audit entries and access logs uploaded; evidence files separately | Central verifies van signature, ignores duplicates, checks audit chain; van deletes local copy only after confirmation |
| M7 | Same consent uploaded twice | Same device ID | Treated as duplicate; no second record |
| M8 | Offline withdrawal vs newer online consent | Compare capture times | Withdrawal always wins over an older or equal consent; a later full consent stays current |
| M9 | Patient withdrew centrally while van was offline | Van has old status | Van gets the withdrawal at next snapshot; until then the van's stale cache is the risk (keep trips' snapshot age short, D-06) |
| M10 | Notice retired while van offline | Van uses old version | Accepted within an offline grace period; re-consent asked if the change was material |
| M11 | Van clock wrong | Capture time outside allowed range | Saved and flagged for review; server time used to order conflicts |
| M12 | Van laptop lost or stolen | Admin revokes the van's key | Uploads signed after revocation rejected; local data encrypted; breach process starts |
| M13 | Guardian OTP with no network | OTP impossible | Fallback per D-03 (ID seen or witness + signature); OTP can be sent later when online |
| M14 | SMS confirmation | No network in van | SMS sent after sync |

## 9. TM (Telemedicine)

The patient is at a spoke (HWC or van) and a specialist at a hub sees their record
remotely; the key question is the remote doctor's right to view.

| # | Scenario | Flow | System result |
|---|---|---|---|
| T1 | Teleconsultation consent | At the spoke, consent screen includes a separate "teleconsultation with a remote doctor" purpose | Remote doctor can view the record only if this purpose is GRANTED |
| T2 | Specialist opens the case | Specialist's API calls carry the purpose; check runs for the specialist | Allowed for the case scope; every view logged against the specialist's user ID |
| T3 | Specialist needs older history or a follow-up later | Time-limited access request → OTP to patient (or guardian) → approve for N days | Access ends automatically; patient can cancel anytime |
| T4 | Patient declines teleconsultation | Purpose DENIED | Case cannot be sent to the hub; spoke offers in-person referral |
| T5 | Patient withdraws during an open case | Withdrawal at spoke | Specialist's access stops at once (cache cleared); open case marked "consent withdrawn" |
| T6 | Video / call recording | Separate purpose if TM records sessions (D-07) | Recording only if GRANTED; recording kept per retention rule |
| T7 | Prescription / advice sent back | Part of treatment purpose | No extra consent; delivered to the spoke |
| T8 | TM in a van, offline | Consent captured offline as in MMU (M2–M8) | Teleconsultation itself needs network; consent syncs first so the hub check passes |
| T9 | Child teleconsultation | Guardian gives consent at the spoke | Guardian also approves any time-limited access OTP |
| T10 | Emergency teleconsultation, patient unable to consent | Hub doctor uses break-glass | 24 h access, reason, DPO review |

## 10. ECD (Early Childhood Development)

ECD is a call-centre service. Agents call mothers about pregnancy and child development,
so consent is verbal. The mother usually consents for herself and as guardian of the child.
The outbound call-list model below is our understanding of ECD; the ECD team should confirm
it (D-08).

| # | Scenario | Flow | System result |
|---|---|---|---|
| E1 | First call to a mother | Agent verifies identity, reads notice script in her language, records yes/no per purpose (counselling calls, SMS, recording) | Verbal consent saved with call ID + recording link |
| E2 | Consent for the child | Mother consents as guardian; child linked to her | Child's consent = GUARDIAN, guardian link to mother |
| E3 | Phone answered by someone else (husband, relative) | Agent does not discuss health details; asks for the mother or a call-back time | No consent saved; call logged as "not reached" |
| E4 | Mother declines the programme | Purpose DENIED | Number removed from call list; no further calls or SMS |
| E5 | Mother agrees to calls but not SMS | Partial consent | Calls continue; SMS blocked |
| E6 | Mother asks to stop calls later | Agent withdraws on her behalf during the call | WITHDRAWN; call schedule cancelled; SMS confirmation |
| E7 | Each scheduled call | Call scheduler runs the consent check before dialling | Only patients with valid consent are dialled |
| E8 | Existing mothers already on call lists (pre-DPDP) | Marked LEGACY | Consent script read at the next call before counselling |
| E9 | Mother is under 18 | Guardian consent needed for her own data (Rule 10) | Agent asks for a parent/guardian; per D-03 |
| E10 | Child death / pregnancy loss recorded | Programme purpose ends | Calls stop; data handled per retention rule |
| E11 | Call recording | Separate notice line that the call is recorded | Recording reference stored as proof |

## 11. Helpline 104 (Health Helpline)

Inbound calls. Today 104 consent is a fixed English script and the answer is not saved.
Under the new design the script is in the caller's language, and each answer is saved with
the call ID.

| # | Scenario | Flow | System result |
|---|---|---|---|
| A1 | New caller giving personal details | Agent reads short notice script in caller's language → yes/no per purpose | Verbal consent saved with call ID + recording link |
| A2 | Caller only wants general information | No personal data recorded | No consent needed; call logged without identity |
| A3 | Known caller, consent valid | Panel shows consent status on caller lookup | Call proceeds |
| A4 | Known caller, consent pending / legacy | Panel prompts the script first | Consent saved before the case is recorded |
| A5 | Calling for someone else (e.g., parent for a child, son for elderly father) | Agent records relationship; if the patient is a child, caller is guardian; for an adult, the adult's own consent is needed (D-09) | Saved with consenting_party and relationship |
| A6 | Caller refuses | Purpose DENIED | Only advice without storing personal data |
| A7 | Call drops during the script | Nothing confirmed | No consent saved; prompt again on next call |
| A8 | Language switch mid-call | Agent switches script language | Consent saved in the language actually read |
| A9 | Caller asks to stop SMS / withdraw | Agent withdraws on the call | WITHDRAWN; SMS confirmation |
| A10 | Caller wants to see, correct or delete data, or complain | Agent creates a rights request / grievance | Reference number by SMS; OTP verification before any data is shared |
| A11 | Medical emergency on call | Transfer to emergency service; minimum data only | Emergency handling; consent taken later or recorded as not possible |
| A12 | Agent or supervisor views caller history | Check + data-access log | Every view logged |

## 12. Helpline 1097 (HIV/AIDS Helpline)

1097 handles highly sensitive, stigma-prone data. Callers must be able to stay anonymous,
and nothing may reveal their status to people around them. These scenarios assume 1097 is
the national HIV/AIDS helpline; confirm with the programme (D-10).

| # | Scenario | Flow | System result |
|---|---|---|---|
| N1 | Anonymous caller (counselling only) | No name or number stored; caller gets an anonymous case ID | No personal data → no consent record; call logged against case ID |
| N2 | Caller agrees to be registered (for callback or referral) | Verbal notice script; separate yes/no for callback, SMS, referral to a testing/treatment centre | Verbal consent saved with call ID; each purpose separate |
| N3 | Callback | Allowed only if "callback" purpose GRANTED; agent confirms identity before speaking | Callback scheduler runs the consent check |
| N4 | SMS | Off by default and wording never mentions HIV/AIDS | SMS only if GRANTED; generic text |
| N5 | Referral to a centre | Shares only the referral details needed, only if GRANTED | Disclosure logged; the receiving centre is listed in the patient's "shared with" record |
| N6 | Caller under 18 | Guardian consent normally required (Rule 10) but may harm the child | Anonymous counselling continues; registration rule = Legal decision (D-11) |
| N7 | Caller withdraws | Agent withdraws on the call | Callbacks/SMS/referral sharing stop; disposition review for stored data |
| N8 | Caller asks to delete everything | Erasure request | Deleted unless law requires retention; result told on call (no SMS) |
| N9 | Access to 1097 records | Restricted role only; masked by default | Every view logged; unusual access alerts the DPO |
| N10 | Call recording | Caller told at start; can continue unrecorded if allowed (D-10) | Recording reference saved only if agreed |

## Open decisions (owner: Legal / DPO / programme teams)

| ID | Decision | Referenced by |
|---|---|---|
| D-01 | (to be listed by the proposal owner) | — |
| D-02 | Treatment allowed while consent is pending at revisit | H4 |
| D-03 | Guardian fallback when OTP is impossible | C7, E9, M13 |
| D-04 | Behaviour when Consent-API is unreachable | C19 |
| D-05 | Re-consent grace period after a material notice change | C14 |
| D-06 | Maximum offline snapshot age for vans | M9 |
| D-07 | Whether TM records sessions | T6 |
| D-08 | ECD outbound call-list model confirmation | §10 |
| D-09 | Consent rules when calling for another adult | A5 |
| D-10 | 1097 programme confirmation and unrecorded-call option | §12, N10 |
| D-11 | Registration rule for 1097 callers under 18 | N6 |
| §13 | Retention periods per data type | C13, E10, T6 |
