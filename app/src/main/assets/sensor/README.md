# UCI HAPT teaching subset

This folder contains a small playback subset derived from:

Reyes-Ortiz, J., Anguita, D., Oneto, L., & Parra, X. (2015). *Smartphone-Based Recognition of Human Activities and Postural Transitions* [Dataset]. UCI Machine Learning Repository. https://doi.org/10.24432/C54G7M

The source dataset is licensed under Creative Commons Attribution 4.0 International (CC BY 4.0). The UCI page documents three-axis acceleration and angular velocity recorded by a waist-mounted Samsung Galaxy S II at 50 Hz. Acceleration is expressed in `g`; gyroscope values are expressed in `rad/s`.

## Included rows

The committed file `uci_hapt_exp01_user01_subset.csv` pairs rows at the same one-based index from:

- `RawData/acc_exp01_user01.txt`
- `RawData/gyro_exp01_user01.txt`

Exact source ranges:

| Activity label | Inclusive source rows | Samples |
| --- | ---: | ---: |
| `STANDING` | 500–549 | 50 |
| `WALKING` | 7650–7699 | 50 |
| `WALKING_DOWNSTAIRS` | 13300–13349 | 50 |

The combined CSV contains 150 samples. Its elapsed time is reconstructed at the documented 50 Hz cadence, so consecutive rows are 20 ms apart. No random or invented axes are included.

## Reproduce the asset

Download and extract the official UCI archive, then run from the repository root:

```powershell
.\tools\extract_uci_hapt_subset.ps1 `
  -DatasetRoot 'C:\path\to\extracted\dataset' `
  -OutputPath '.\app\src\main\assets\sensor\uci_hapt_exp01_user01_subset.csv'
```

This asset replays published research data for a classroom demonstration. It is not a measurement from the current app user and must not be represented as a medical or wearable reading.
