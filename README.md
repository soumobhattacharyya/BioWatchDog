# BioWatchDog 🐕‍🦺

**A continuous, privacy-conscious behavioral-biometric risk engine** built with Java, Spring Boot, WebSockets, ONNX Runtime, and a Python/PyTorch training pipeline.

BioWatchDog watches *short windows* of voluntarily collected keyboard timing and mouse movement features during a demo session, compares them with an enrolled behavioral profile, and reports a **risk signal**. A production deployment should use the signal to request step-up authentication, not as the sole proof of identity or as an automatic irreversible lockout.

> **Research/demo software.** This repository is a development scaffold, not a security product. The first version deliberately fails closed for ML scoring when no trained ONNX model is configured. Do not collect real credentials, key contents, text typed, or use this system to make high-impact decisions. Obtain informed consent, provide a clear opt-out, minimize retention, and test for accessibility and demographic bias before any real deployment.

## What is included

- **Java 21 + Spring Boot 3** HTTP/WebSocket gateway.
- Bounded event windows, strict event validation, feature extraction, per-session state, and a policy result (`LOW_RISK`, `STEP_UP_RECOMMENDED`, `INSUFFICIENT_DATA`, `MODEL_UNAVAILABLE`).
- **ONNX Runtime Java** adapter that expects a trained embedding model; no fabricated “AI similarity” fallback.
- Static browser demo: explicit start/stop, keyboard *timings only* (never key values or typed text), pointer vectors, visible consent notice, local event count.
- Python/PyTorch example for training a Siamese-style encoder with contrastive loss and exporting to ONNX.
- Dockerfile, docker-compose, tests, and a milestone checklist.

## Architecture

```text
Browser (explicit opt-in; no key values/text)
  │ WebSocket: timing / pointer event JSON
  ▼
Spring Boot gateway
  ├─ validates event type, size, timestamp, and session
  ├─ buffers a bounded sliding window
  ├─ aggregates a fixed-length numeric feature vector
  ▼
ONNX Runtime Java
  ├─ model loaded only when BIOWATCHDOG_MODEL_PATH is configured
  └─ emits an embedding; similarity against enrolled profile
  ▼
Policy layer
  ├─ enough samples + model available + calibrated threshold -> risk signal
  └─ otherwise -> insufficient data / step-up / model unavailable
```

## Prerequisites

- Java 21+
- Maven 3.9+
- Python 3.11+ for model training (CPU works; a GPU is optional)
- Docker (optional)

## Run locally

```bash
cd backend
mvn spring-boot:run
```

Open <http://localhost:8080>. The demo browser client starts only when you explicitly press **Start demo**. WebSocket endpoint: `/ws/events?sessionId=<uuid>`.

Run backend tests:

```bash
cd backend
mvn test
```

## ML pipeline

The training script expects a CSV with one row per event window and columns:

- `subject_id`: pseudonymous participant ID used only for pair generation
- `f0` ... `f15`: 16 numeric features in the same order as `FeatureExtractor`

Example data format is documented in `ml/README.md`. Do not use synthetic scores as evidence of performance. For real experiments, use consented data, participant-disjoint evaluation, and report FAR/FRR, EER, ROC-AUC, confidence intervals, and calibration. Never evaluate by randomly splitting adjacent windows from the same recording across train and test.

```bash
cd ml
python -m venv .venv
# Linux/macOS:
source .venv/bin/activate
# Windows PowerShell: .venv\Scripts\Activate.ps1
pip install -r requirements.txt
python train_siamese.py --csv data.csv --output ../artifacts/biowatchdog-encoder.onnx
```

Then start the backend with:

```bash
BIOWATCHDOG_MODEL_PATH=/absolute/path/to/biowatchdog-encoder.onnx mvn -f backend/pom.xml spring-boot:run
```

The ONNX artifact is only an **encoder**. Before interpreting cosine similarity as authentication evidence, implement enrollment, profile versioning, operating-point calibration on held-out users, liveness/spoof testing, and replay defenses. The supplied backend deliberately returns `MODEL_UNAVAILABLE` when a valid enrolled reference embedding or a compatible model is absent.

## API / message protocol

WebSocket client sends messages like:

```json
{"type":"KEY","eventTime":1730000000000,"dwellMs":92,"flightMs":140}
{"type":"POINTER","eventTime":1730000000100,"x":0.42,"y":0.28}
```

`KEY` messages contain **no key code or character**. `x` and `y` are normalized to `[0,1]` in the browser. The backend keeps only the most recent bounded window in memory and returns an aggregate only after a minimum sample count.

Health check: `GET /api/health`
Current in-memory demo session count: `GET /api/sessions` (metadata only; local demo endpoint).

## Security & privacy checklist before real use

- [ ] Explicit informed consent, opt-out, and accessible alternative authentication.
- [ ] TLS/WSS, authentication of session creation, origin allowlist, rate limits, CSRF/session protections.
- [ ] No text content, key codes, form fields, passwords, clipboard or browser-history data captured.
- [ ] No raw event persistence by default; retention and deletion policy documented.
- [ ] Secure enrollment, encrypted profile store, model integrity checks and versioning.
- [ ] Participant-disjoint testing; calibrate false accept/reject rates for the deployment domain.
- [ ] Step-up flow and human-reviewed recovery; no sole reliance on behavioral biometrics.
- [ ] Accessibility, disability, device, fatigue, injury, and demographic subgroup evaluation.
- [ ] Threat modeling for synthetic-event injection, replay, session hijacking, browser automation, and model inversion.

## Project roadmap

1. **MVP (this repo):** browser telemetry, bounded buffers, Java features, fail-safe ONNX adapter and demo UI.
2. **Model validation:** consented datasets, pair-generation protocol, reproducible training, subject-disjoint metrics.
3. **Enrollment/profile store:** encrypted reference embeddings, lifecycle, revocation and drift handling.
4. **Security hardening:** authenticated session bootstrap, WSS, CSP, origin policy, rate limiting, audit events without raw biometrics.
5. **Decision workflow:** demo step-up challenge (e.g. TOTP/WebAuthn integration), accessible recovery, observability.
6. **Deployment:** CI checks, image scanning, monitoring and a formal privacy/threat review.

## License

MIT. See `LICENSE`.
