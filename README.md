<div align="center">

 ![hyperfoil_logo](https://user-images.githubusercontent.com/91419219/228698725-f281b5cc-7a36-4a21-b86f-995a3bddd205.png)


<a href="https://github.com/Hyperfoil/Hyperfoil/issues"><img alt="GitHub issues" src="https://img.shields.io/github/issues/Hyperfoil/Hyperfoil"></a>
<a href="https://github.com/Hyperfoil/Horreum/fork"><img alt="GitHub forks" src="https://img.shields.io/github/forks/Hyperfoil/Hyperfoil"></a>
<a href="https://github.com/Hyperfoil/Hyperfoil/stargazers"><img alt="GitHub stars" src="https://img.shields.io/github/stars/Hyperfoil/Hyperfoil"></a>
<a href="https://github.com/Hyperfoil/Hyperfoil//blob/main/LICENSE"><img alt="GitHub license" src="https://img.shields.io/github/license/Hyperfoil/Hyperfoil"></a> 
</div>

Hyperfoil is microservice-oriented distributed benchmark framework
that solves the [coordinated-omission fallacy](https://www.slideshare.net/InfoQ/how-not-to-measure-latency-60111840).

Project website: [https://hyperfoil.io](https://hyperfoil.io).

## Prerequisites

* Java 21
* [Apache Maven 3.8](https://maven.apache.org/)

## Getting Started

```bash
mvn package
```

To run without test cases do

```bash
mvn -DskipTests=true package
```

Then the distribution is either in `distribution/target/hyperfoil-<version>-SNAPSHOT.zip` or in

``` bash
cd distribution/target/distribution/
```

## Image

We publish the image at [quay.io/hyperfoil/hyperfoil](https://quay.io/repository/hyperfoil/hyperfoil?tab=tags).

## Testing

### Running All Tests

To run all tests including clustered benchmark tests:

```bash
mvn clean package -Pbenchmark
```

### Clustered Tests SSH Requirements

Some tests (tagged with `@Tag("io.hyperfoil.test.Benchmark")`) require SSH connectivity to localhost for deploying clustered agents.

If these tests fail with errors like:
- "Connection refused"
- "No such file or directory" for SSH key

Follow these steps:

1. **Set up SSH server and passwordless authentication**:
   ```bash
   # Start SSH daemon (if not running)
   sudo systemctl start sshd
   
   # Generate SSH key if it doesn't exist (will prompt if file exists)
   ssh-keygen -t rsa -b 4096 -N "" -f ~/.ssh/id_rsa
   
   # Set up passwordless SSH to localhost
   ssh-copy-id $(whoami)@localhost
   ```

2. **Verify the setup** by connecting without password:
   ```bash
   ssh $(whoami)@localhost
   ```
   You should be able to connect without entering a password.

## Troubleshooting
- Clean up the folder `/tmp/hyperfoil` and add `-Dio.hyperfoil.controller.log.level=debug` VM option
- Add the `CounterProcessor` handler
- Run the benchmark
- Filter by `grep -E "ControllerPhase|CounterProcessor" /tmp/hyperfoil/hyperfoil.local.log` or use the following one-line script to generate a report
- The values are an approximation per phase and status because it will log when `max` is reached. You can control it by `CounterProcessor(int max)`
```bash
grep -E "ControllerPhase|CounterProcessor" /tmp/hyperfoil/hyperfoil.local.log | awk '/ControllerPhase/ { if (p) printf "%s %-55s Sent: %-8d Recv: %-8d\n", ts, p, s, r; ts=$1; p=$6 " [" $9 " -> " $11 "]"; s=0; r=0 } /CounterProcessor/ { if ($5 == "Sent") s+=$6; else if ($5 == "Received") r+=$6 } END { printf "%s %-55s Sent: %-8d Recv: %-8d\n", ts, p, s, r }'
```
### Output example
```text
19:10:12,533 calibration [NOT_STARTED -> STARTING]                   Sent: 0        Recv: 0       
19:10:12,538 calibration [STARTING -> RUNNING]                       Sent: 29000    Recv: 29000   
19:10:18,533 calibration [RUNNING -> FINISHING]                      Sent: 0        Recv: 0       
19:10:18,536 calibration [FINISHING -> FINISHED]                     Sent: 270000   Recv: 270000  
19:11:11,287 calibration [FINISHED -> TERMINATED]                    Sent: 0        Recv: 0       
19:11:11,287 test [NOT_STARTED -> STARTING]                          Sent: 0        Recv: 0       
19:11:11,304 test [STARTING -> RUNNING]                              Sent: 51000    Recv: 51000   
19:11:21,288 test [RUNNING -> FINISHING]                             Sent: 0        Recv: 0       
19:11:21,289 test [FINISHING -> FINISHED]                            Sent: 0        Recv: 0       
19:11:21,289 test [FINISHED -> TERMINATING]                          Sent: 0        Recv: 0       
19:11:22,131 test [TERMINATING -> TERMINATED]                        Sent: 0        Recv: 0       
```

## Contributing

Contributions to `Hyperfoil` are managed on [GitHub.com](https://github.com/Hyperfoil/Hyperfoil/)

* [Ask a question](https://github.com/Hyperfoil/Hyperfoil/discussions)
* [Raise an issue](https://github.com/Hyperfoil/Hyperfoil/issues)
* [Feature request](https://github.com/Hyperfoil/Hyperfoil/issues)
* [Code submission](https://github.com/Hyperfoil/Hyperfoil/pulls)

Checkout the [Contributing guide](./CONTRIBUTING.md) for more details and suggestions on how to setup the project.

You can reach the community on [Zulip](http://hyperfoil.zulipchat.com).

Please, consult our [Code of Conduct](./CODE_OF_CONDUCT.md) policies for interacting in our community.

Consider giving the project a [star](https://github.com/Hyperfoil/Hyperfoil/stargazers) :star: on [GitHub](https://github.com/Hyperfoil/Hyperfoil/) if you find it useful.

## License

[Apache-2.0 license](https://opensource.org/licenses/Apache-2.0)
