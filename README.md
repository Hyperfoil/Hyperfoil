# Hyperfoil

Hyperfoil is microservice-oriented distributed benchmark framework.

Project website: [https://hyperfoil.io](https://hyperfoil.io).

## Prerequisites

* Java 11
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

## Contributing

Contributions to `Hyperfoil` are managed on [GitHub.com](https://github.com/Hyperfoil/Hyperfoil/)

* [Ask a question](https://github.com/Hyperfoil/Hyperfoil/discussions)
* [Raise an issue](https://github.com/Hyperfoil/Hyperfoil/issues)
* [Feature request](https://github.com/Hyperfoil/Hyperfoil/issues)
* [Code submission](https://github.com/Hyperfoil/Hyperfoil/pulls)

Contributions are most welcome !

You can reach the community on [Zulip](http://hyperfoil.zulipchat.com).

Please, consult our [Code of Conduct](./CODE_OF_CONDUCT.md) policies for interacting in our
community.

Consider giving the project a [star](https://github.com/Hyperfoil/Hyperfoil/stargazers) on
[GitHub](https://github.com/Hyperfoil/Hyperfoil/) if you find it useful.

## License

[Apache-2.0 license](https://opensource.org/licenses/Apache-2.0)
