# Multi-Context-Incremental-Reasoning-over-Data-Streams

## Requirements
* Requires Scala 2.12.2 or higher
* Building should be done by using SBT (Scala Built Tools) with version 0.13 or higher.
  
* /!\ avoid sbt version override /!\

## Build

First `sbt compile` should be executed.

Then a new jar-package can be created with `sbt assembly`.

## Launch program

Must deploy folder `dataset\csparql_web_server` on server (tomcat, jboss ..) , port 9000

```Run Program```
### arguments Examples
#### Queries
```--queries=csparql_query/Q1.txt```

```--queries=csparql_query/Q1.txt,csparql_query/Q2.txt```


#### ATMS

``` 
--program src/test/resources/Q7.lars -l debug -r incrementalAtms -f all -e change
```

#### JTMS

``` --program src/test/resources/Q7.lars -l debug -r incremental -f all -e change  ```


### CMD

```
sbt assembly
 
tail -n 0 -F input.txt | java -jar sma_processing-assembly-1.0.jar --program src/test/resources/Q7.lars >> out.txt
```

## ATMS SOURCE CODE
https://github.com/wmebrek/atms