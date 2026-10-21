<!-- OPERATIONS_TABLE_START -->

Dijkstra logical operations: column medians across 3 seeds; counted outside timing. Full records: [dijkstra-operations.csv](dijkstra-operations.csv).

| Family | V | E / weight checks | Arc scans / relaxation attempts | Successful relaxations | Pushes | Pops | Stale pops | Settled vertices | Peak queue |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sparse | 100 | 400 | 800 | 182 | 183 | 183 | 83 | 100 | 113 |
| sparse | 500 | 2000 | 4000 | 916 | 917 | 917 | 417 | 500 | 542 |
| sparse | 1000 | 4000 | 8000 | 1803 | 1804 | 1804 | 804 | 1000 | 1068 |
| sparse | 3000 | 12000 | 24000 | 5394 | 5395 | 5395 | 2395 | 3000 | 3221 |
| dense | 100 | 1237 | 2474 | 292 | 293 | 293 | 193 | 100 | 219 |
| dense | 200 | 4975 | 9950 | 704 | 705 | 705 | 505 | 200 | 569 |
| dense | 500 | 31187 | 62374 | 2134 | 2135 | 2135 | 1635 | 500 | 1781 |
| dense | 1000 | 124875 | 249750 | 5034 | 5035 | 5035 | 4035 | 1000 | 4346 |

<!-- OPERATIONS_TABLE_END -->