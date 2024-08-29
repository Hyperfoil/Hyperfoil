---
title: "randomFile"
description: "Reads bytes from a randomly chosen file into a variable. "
---
Reads bytes from a randomly chosen file into a variable. <br> Two formats are supported: <br> Example 1 - without weights: <br> 
```
toVar: myVar
files:
- /path/to/file1.txt
- file2_relative_to_benchmark.txt
```
 

 Example 2 - with weights (the second file will be returned twice as often): <br> 
```
toVar: myVar
files:
  /path/to/file1.txt: 1
  file2_relative_to_benchmark.txt: 2
```


| Property | Type | Description |
| ------- | ------- | -------- |
| filenameVar | String | Optional variable to store the filename of the random file. |
| files | [Builder](#files) | Potentially weighted list of files to choose from. |
| toVar | String | Variable where the chosen byte array should be stored. |

### files

| Property | Type | Description |
| ------- | ------- | ------- |
| &lt;any&gt; | &lt;list of strings&gt; | Item as the key and weight (arbitrary floating-point number, defaults to 1.0) as the value. |
| &lt;list of strings&gt; | &lt;list of strings&gt; | Item as the key and weight (arbitrary floating-point number, defaults to 1.0) as the value. |

