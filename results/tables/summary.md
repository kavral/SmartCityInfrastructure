<!-- BENCHMARK_TABLE_START -->

Median runtime in milliseconds (81 samples per cell: 3 JVMs x 3 seeds x 9 runs). Full quartiles and ranges: [summary.csv](summary.csv).

| Family | V | E | Kruskal | Prim | Dijkstra | Topological | Kosaraju |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| sparse | 100 | 400 | 0.1212 | 0.0996 | 0.0176 | 0.0047 | 0.0203 |
| sparse | 500 | 2000 | 0.7955 | 0.6917 | 0.1632 | 0.0287 | 0.1110 |
| sparse | 1000 | 4000 | 1.6946 | 1.5786 | 0.3591 | 0.0594 | 0.2235 |
| sparse | 3000 | 12000 | 5.4097 | 5.5827 | 1.2548 | 0.1790 | 0.6933 |
| dense | 100 | 1237 | 0.4337 | 0.3846 | 0.0463 | 0.0112 | 0.0525 |
| dense | 200 | 4975 | 1.9737 | 1.9278 | 0.1430 | 0.0423 | 0.1913 |
| dense | 500 | 31187 | 15.1936 | 16.5283 | 0.6802 | 0.2980 | 1.3114 |
| dense | 1000 | 124875 | 70.4915 | 93.0361 | 2.9234 | 1.7369 | 6.7748 |

<!-- BENCHMARK_TABLE_END -->