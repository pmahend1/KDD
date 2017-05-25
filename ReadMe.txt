Instructions
***********************************
0) Copy Jar file RandomForest_group3.jar to DSBA server workspace /users/<ninernet_username> by drag and drop - WinScp

1) Copy data file and attribute file onto DSBA server workspace /users/<ninernet_username> by drag and drop - WinScp

2) Copy the folder and files onto hadoop (hdfs) cluster with below commands

Commands used
-------------------

hadoop fs -put /users/pmahend1/Car
hadoop fs -put /users/pmahend1/Mammographic

3) Run forest algorithm by the jar file provided RandomForest_group3.jar

Format :

hadoop jar [complete-jar-file-path-in-workspace] [package.Class-with-Main-Method] [attributes-file-path-in-cluster] [data-file-name-in-cluster] [OutputFolder-for-ActionRules] [OutputFolder-for-AssociationRules]

Note : [OutputFolder-for-ActionRules] [OutputFolder-for-AssociationRules] should not be preexisting in cluster.

Commands used:
---------------

hadoop jar /users/pmahend1/Jars/RandomForest_group3.jar randomForest.group3.RandomForest Car/car.c45-names.attributes.txt Car/car.data.txt Car/ActionRulesOutput Car/AssociationActionRulesOutput

hadoop jar /users/pmahend1/Jars/RandomForest_group3.jar randomForest.group3.RandomForest Mammographic/mammographic_attribute.txt Mammographic/mammographic_masses.data.txt Mammographic/ActionRulesOutput Mammographic/AssociationActionRulesOutput

4) Provide stable attributes with command line interface

5) Provide decision attributes with command line interface

6) Provide the Decision_From and Decision_To values

7) There will be 2 jobs running one after another . 1 for action rules and 1 for association rules.
Wait for them to finish

8) Once completed look for output in cluster folder /hdfs/user/<ninernet_username>

9) You can drag and drop or use below command to fetch data to your workspace

hadoop fs -get [folder-name-in-cluster] [folder-name-in-workspace]