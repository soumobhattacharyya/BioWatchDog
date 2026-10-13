# Contributing

1. Open an issue before substantial architectural changes.
2. Never commit personal/biometric data, real browser captures, trained weights, credentials, or secrets.
3. Add tests for event validation, windowing, feature order, ONNX compatibility, and policy behavior.
4. Keep model decisions explicit and auditable; never silently fall back to a hand-written score when inference fails.
5. Do not record key values, typed text, form inputs, clipboard, or passwords.
