# Vericov

Vericov is an agentic coverage platform for coverage reporting, merge confidence,
and autonomous test remediation workflows.

This initial Python package reserves the `vericov` distribution name while the
public Python client and CLI are prepared. The package currently exposes version
metadata only.

## Installation

```bash
pip install vericov
```

## Coverage Upload CLI

The coverage upload CLI lives in `clis/coverage-upload` as an independent
Python package. It installs the `vericov` console script for CI uploads:

```bash
cd clis/coverage-upload
python3 -m pip install -e .
VERICOV_API_KEY=vc_live_... vericov upload --coverage coverage/lcov.info
```

See `clis/coverage-upload/README.md` for usage, development, and contribution
guidelines.

## Local Backend Stack

Bring up Supabase, the Vericov Helidon services, and the product Kong gateway:

```bash
./scripts/dev-up.sh
```

The public product gateway listens at `http://localhost:9000`. For faster Java iteration while keeping Supabase and Kong in Docker, run:

```bash
./scripts/dev-up.sh --host-java
```

Stop the local stack without deleting Supabase data:

```bash
./scripts/dev-down.sh
```
