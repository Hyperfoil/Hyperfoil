<!-- If your PR fixes an open issue, use `Closes #435` or `Fixes #435` to link your PR with the issue. #435 stands for the issue number you are fixing -->

## Fixes Issue

<!-- Remove this section if not applicable -->

<!-- Example: Fixes #31 -->

## Changes proposed

<!-- List all the proposed changes in your PR -->

<!-- Mark all the applicable boxes. To mark the box as done follow the following conventions -->
<!--
- [x] Correct; marked as done
- [X] Correct; marked as done

- [ ] Not correct; marked as **not** done
-->

## Check List (check all the applicable boxes) <!-- Follow the above conventions to check the box -->

- [ ] My change requires changes to the documentation.
- [ ] I have updated the documentation accordingly.
- [ ] All new and existing tests passed.

> Note that the documentation is located at [github.com/Hyperfoil/hyperfoil.github.io](https://github.com/Hyperfoil/hyperfoil.github.io/) 

<details>
<summary>
How to backport a pull request to the current <em>stable</em> branch?
</summary>

In order to automatically create a **backporting pull request** please add `backport` label to the current pull request.

Then, as soon as the pull request is merged into the `master` branch, the process will try to automatically open a backporting pull request against the _stable_ branch. If something goes wrong, a comment will be added in the original pull request such that all people involved get notified.

> The `backport` label can be also added after the pull  request has been merged.

> If the process fails due to temporary problems, such as connectivity problems, consider removing and re-adding the `backport` label.
</details>