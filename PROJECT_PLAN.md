# BioWatchDog implementation plan

## MVP implemented in this repository

- [x] Java 21 Spring Boot application
- [x] Consent-first local browser interface
- [x] Browser sends keyboard timing only and pointer positions; no key value/text payloads
- [x] Bounded in-memory sliding window
- [x] Strict event validation and explicit error messages
- [x] Fixed 16-dimension feature extractor shared with Python feature-order documentation
- [x] ONNX Runtime Java adapter with no model-score fallback
- [x] Honest policy statuses when event count/model/enrollment is missing
- [x] Unit tests, CI workflow and Docker packaging
- [x] PyTorch Siamese-style training/export baseline

## Important remaining engineering work

- [ ] Add cross-language golden test fixtures for feature scaling and inference outputs.
- [ ] Apply the exported scaler in Java (or export preprocessing into the ONNX graph).
- [ ] Implement enrollment/reference embedding storage and explicit consent-based re-enrollment.
- [ ] Implement cosine similarity against enrolled profile only after calibration and held-out evaluation.
- [ ] Add an authenticated session-bootstrap endpoint and signed/short-lived session tickets.
- [ ] Add WebAuthn/TOTP step-up challenge; behavioral signal must not be sole factor.
- [ ] Configure TLS/WSS at a trusted reverse proxy, origin allowlist, rate limiting, CSP and secure headers.
- [ ] Add expiration for abandoned sessions and bounded global session capacity.
- [ ] Add anti-replay / synthetic-event threat model and tests.
- [ ] Evaluate false accept/reject rates with subject-disjoint and time-separated testing.
- [ ] Accessibility and subgroup evaluation; provide an accessible non-biometric fallback.

## Definition of done for a public portfolio release

A clean clone must start with documented prerequisites; tests must pass; the model must not be claimed to authenticate until preprocessing, enrollment, calibration, and validation exist; no private data, secrets, or model datasets are committed; and the README must contain actual measured results rather than invented accuracy numbers.
