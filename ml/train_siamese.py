#!/usr/bin/env python3
"""Train a small Siamese-style encoder and export the encoder to ONNX.

This script is a baseline for experimentation, not production authentication.
"""
from __future__ import annotations

import argparse
import random
from pathlib import Path

import numpy as np
import pandas as pd
import torch
from sklearn.model_selection import GroupShuffleSplit
from sklearn.preprocessing import StandardScaler
from torch import nn
from torch.utils.data import DataLoader, Dataset

FEATURES = [f"f{i}" for i in range(16)]


class Encoder(nn.Module):
    def __init__(self, input_size: int = 16, embedding_size: int = 32) -> None:
        super().__init__()
        self.network = nn.Sequential(
            nn.Linear(input_size, 64), nn.ReLU(), nn.Dropout(0.10),
            nn.Linear(64, 48), nn.ReLU(), nn.Linear(48, embedding_size),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return nn.functional.normalize(self.network(x), p=2, dim=1)


class PairDataset(Dataset):
    def __init__(self, features: np.ndarray, subjects: np.ndarray, pairs: int, seed: int) -> None:
        self.x = torch.tensor(features, dtype=torch.float32)
        self.subjects = np.asarray(subjects)
        self.by_subject = {subject: np.flatnonzero(self.subjects == subject) for subject in np.unique(subjects)}
        self.labels = np.unique(subjects)
        if len(self.labels) < 2:
            raise ValueError("At least two distinct subjects are required for pair training")
        rng = np.random.default_rng(seed)
        self.pairs: list[tuple[int, int, float]] = []
        for _ in range(pairs):
            same = bool(rng.integers(0, 2))
            if same:
                subject = rng.choice(self.labels)
                indexes = self.by_subject[subject]
                if len(indexes) < 2:
                    same = False
                else:
                    i, j = rng.choice(indexes, size=2, replace=False)
            if not same:
                s1, s2 = rng.choice(self.labels, size=2, replace=False)
                i = rng.choice(self.by_subject[s1])
                j = rng.choice(self.by_subject[s2])
            self.pairs.append((int(i), int(j), 1.0 if same else 0.0))

    def __len__(self) -> int:
        return len(self.pairs)

    def __getitem__(self, index: int) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
        i, j, same = self.pairs[index]
        return self.x[i], self.x[j], torch.tensor(same, dtype=torch.float32)


def contrastive_loss(a: torch.Tensor, b: torch.Tensor, same: torch.Tensor, margin: float = 1.0) -> torch.Tensor:
    distance = torch.linalg.vector_norm(a - b, dim=1)
    positive = same * distance.square()
    negative = (1.0 - same) * torch.relu(margin - distance).square()
    return (positive + negative).mean()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--csv", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--epochs", type=int, default=25)
    parser.add_argument("--pairs-per-epoch", type=int, default=4096)
    parser.add_argument("--batch-size", type=int, default=128)
    parser.add_argument("--learning-rate", type=float, default=1e-3)
    parser.add_argument("--seed", type=int, default=42)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.epochs < 1 or args.pairs_per_epoch < 32:
        raise ValueError("epochs must be >= 1 and pairs-per-epoch must be >= 32")
    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)

    frame = pd.read_csv(args.csv)
    missing = {"subject_id", *FEATURES} - set(frame.columns)
    if missing:
        raise ValueError(f"CSV is missing required columns: {sorted(missing)}")
    frame = frame[["subject_id", *FEATURES]].dropna()
    frame[FEATURES] = frame[FEATURES].replace([np.inf, -np.inf], np.nan)
    frame = frame.dropna(subset=FEATURES)
    if len(frame) < 8 or frame.subject_id.nunique() < 2:
        raise ValueError("Need at least 8 clean windows from at least 2 subjects")

    x = frame[FEATURES].to_numpy(dtype=np.float32)
    y = frame.subject_id.astype(str).to_numpy()
    # The group split avoids leaking a subject across train/validation. Validation is an
    # engineering sanity check only, not sufficient for a security-performance claim.
    splitter = GroupShuffleSplit(n_splits=1, test_size=0.25, random_state=args.seed)
    train_index, validation_index = next(splitter.split(x, y, groups=y))
    train_x, train_y = x[train_index], y[train_index]
    validation_x, validation_y = x[validation_index], y[validation_index]
    if len(np.unique(train_y)) < 2:
        raise ValueError("Training split needs at least two subjects; add more subjects/windows")
    scaler = StandardScaler().fit(train_x)
    train_x = scaler.transform(train_x).astype(np.float32)
    validation_x = scaler.transform(validation_x).astype(np.float32)
    # Java inference currently supplies unstandardized features, so save scaling parameters
    # for the Java preprocessing step before using a trained model.
    args.output.parent.mkdir(parents=True, exist_ok=True)
    np.savez(args.output.with_suffix(".scaler.npz"), mean=scaler.mean_, scale=scaler.scale_)

    dataset = PairDataset(train_x, train_y, args.pairs_per_epoch, args.seed)
    loader = DataLoader(dataset, batch_size=args.batch_size, shuffle=True)
    model = Encoder()
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.learning_rate, weight_decay=1e-4)
    model.train()
    for epoch in range(args.epochs):
        losses = []
        for left, right, same in loader:
            optimizer.zero_grad(set_to_none=True)
            loss = contrastive_loss(model(left), model(right), same)
            loss.backward()
            optimizer.step()
            losses.append(float(loss.detach()))
        print(f"epoch={epoch + 1:03d}/{args.epochs} contrastive_loss={np.mean(losses):.5f}")

    model.eval()
    example = torch.zeros((1, 16), dtype=torch.float32)
    torch.onnx.export(
        model, example, str(args.output), input_names=["features"], output_names=["embedding"],
        dynamic_axes={"features": {0: "batch"}, "embedding": {0: "batch"}},
        opset_version=17, dynamo=False,
    )
    print(f"Exported encoder: {args.output}")
    print(f"Scaler parameters: {args.output.with_suffix('.scaler.npz')}")
    print(f"Participant-disjoint sanity holdout rows: {len(validation_x)}; subjects: {len(np.unique(validation_y))}")
    print("IMPORTANT: Java feature standardization + enrollment/profile comparison are not implemented yet.")
    print("Do not enable similarity-based policy actions until preprocessing and validation are complete.")


if __name__ == "__main__":
    main()
