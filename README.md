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

## 🧑‍💻 Contributing

Contributions to `Hyperfoil` Please check our [CONTRIBUTING.md](https://github.com/Hyperfoil/Horreum/blob/master/CONTRIBUTING.md)

### If you have any idea or doubt 👇

* [Ask a question](https://github.com/Hyperfoil/Hyperfoil/discussions)
* [Raise an issue](https://github.com/Hyperfoil/Hyperfoil/issues)
* [Feature request](https://github.com/Hyperfoil/Hyperfoil/issues)
* [Code submission](https://github.com/Hyperfoil/Hyperfoil/pulls)

Contribution is the best way to support and get involved in community !

Please, consult our [Code of Conduct](./CODE_OF_CONDUCT.md) policies for interacting in our
community.

Consider giving the project a [star](https://github.com/Hyperfoil/Hyperfoil/stargazers) on
[GitHub](https://github.com/Hyperfoil/Hyperfoil/) if you find it useful.

## License

[Apache-2.0 license](https://opensource.org/licenses/Apache-2.0)
