# BioWatchDog ML pipeline

This is a reproducible **research baseline**, not a validated biometric authenticator.

## Dataset contract

Input CSV columns: `subject_id,f0,f1,...,f15`; each row represents one time window. `subject_id` must be a pseudonymous subject label and must not contain names, email addresses, student IDs, or other direct identifiers. Features and preprocessing must match Java `FeatureExtractor` exactly. The current 16-feature Java extractor computes mean, population standard deviation, min and max for dwell time, flight time, normalized pointer x and normalized pointer y. Missing event types produce zero features; this simple choice needs to be studied because it may expose collection differences.

## What the script does

- Robust standardization values are learned from training subjects only.
- Positive pairs use two windows from one subject; negative pairs use different subjects.
- A shared MLP encoder is trained with contrastive loss.
- The encoder (not the full enrollment/decision system) is exported to ONNX with input name `features`, shape `[batch,16]`, output name `embedding`, shape `[batch,32]`.

The current script is intended to demonstrate the training and export path. Use participant-disjoint splits for evaluation and do not claim security from a good random-window split. Real deployment requires independent calibration, anti-spoof evaluation, device/context shift evaluation, embedding enrollment storage, model/version management, and threshold selection that meets a stated false-accept target.
