package common

object Constantes {

  val REQ_Q1 = "REGISTER QUERY Q1 AS select ?obId1 ?obId2 ?v1 ?v2\n from stream <http://www.insight-centre.org/dataset/SampleEventService#AarhusTrafficData182955> [range 3000ms step 1s]\n from stream <http://www.insight-centre.org/dataset/SampleEventService#AarhusTrafficData158505> [range 3000ms step 1s]\n FROM <http://127.0.0.1:9000/WebGlCity/RDF/SensorRepository.rdf> \n \nwhere {\n\n?p1   a <http://www.insight-centre.org/citytraffic#CongestionLevel>.\n?p2   a <http://www.insight-centre.org/citytraffic#CongestionLevel>.\n\n \n{\n?obId1 <http://purl.oclc.org/NET/ssnx/ssn#observedProperty> ?p1.\n?obId1 <http://purl.oclc.org/NET/sao/hasValue> ?v1.\n?obId1 <http://purl.oclc.org/NET/ssnx/ssn#observedBy> <http://www.insight-centre.org/dataset/SampleEventService#AarhusTrafficData182955>.\n}\n\n{\n?obId2 <http://purl.oclc.org/NET/ssnx/ssn#observedProperty> ?p2.\n?obId2 <http://purl.oclc.org/NET/sao/hasValue> ?v2.\n?obId2 <http://purl.oclc.org/NET/ssnx/ssn#observedBy> <http://www.insight-centre.org/dataset/SampleEventService#AarhusTrafficData158505>.\n}}"

  var REQ_Q1_ol = "REGISTER QUERY Q1 AS SELECT ?obId1 ?obId2 ?v1 ?v2 " +
    "from stream <http://www.insight-centre.org/dataset/SampleEventService#AarhusTrafficData182955> [range 3000ms step 1s] " +
    "from stream <http://www.insight-centre.org/dataset/SampleEventService#AarhusTrafficData158505> [range 3000ms step 1s] " +
    "FROM <http://127.0.0.1:9000/WebGlCity/RDF/SensorRepository.rdf> " +
    "where { " +
    "?p1 a <http://www.insight-centre.org/citytraffic#CongestionLevel>. " +
    "?p2 a <http://www.insight-centre.org/citytraffic#CongestionLevel>. " +
    "{ " +
    "?obId1 <http://purl.oclc.org/NET/ssnx/ssn#observedProperty> ?p1. " +
    "?obId1 <http://purl.oclc.org/NET/sao/hasValue> ?v1. " +
    "?obId1 <http://purl.oclc.org/NET/ssnx/ssn#observedBy> <http://www.insight-centre.org/dataset/SampleEventService#AarhusTrafficData182955>. " +
    "} " +
    "{ " +
    "?obId2 <http://purl.oclc.org/NET/ssnx/ssn#observedProperty> ?p2. " +
    "?obId2 <http://purl.oclc.org/NET/sao/hasValue> ?v2. " +
    "?obId2 <http://purl.oclc.org/NET/ssnx/ssn#observedBy> <http://www.insight-centre.org/dataset/SampleEventService#AarhusTrafficData158505>. " +
    "}}"

  var REQ_TEMPERATURE: String = "REGISTER STREAM cleangenerique AS " +
    "PREFIX :<http://ecareathome.org/stream#> " +
    "PREFIX rdf:<http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
    "PREFIX xsd:<http://www.w3.org/2001/XMLSchema#> " +
    "PREFIX dul:<http://www.ontologydesignpatterns.org/ont/dul/DUL.owl#> " +
    "PREFIX rdg:<http://users.abo.fi/ndiaz/public/HumanActivity.owl#> " +
    "PREFIX sosa: <http://www.w3.org/ns/sosa/> " +
    "CONSTRUCT " +
    "{ " +
    "_:c0 dul:isObservableAt _:c1 . " +
    "_:c0 sosa:isObservedBy ?sensor . " +
    "_:c0 sosa:hasSimpleResult ?avg . " +
    "_:c0 sosa:madeBySensor ?sensorLabel ." +
    "_:c0 rdg:isLocatedIn ?location ." +
    "_:c1 rdf:type dul:TimeInterval . " +
    "_:c1 rdg:hasStartDatetime  ?minTime . " +
    "_:c1 rdg:hasEndDatetime  ?maxTime . " +
    "} " +
    "FROM STREAM <http://ecareathome.org/stream#generique> [ RANGE 10s STEP 10s] " +
    "FROM <http://www.w3.org/ns/sosa> " +
    "WHERE " +
    "{ { SELECT ?sensor ?sensorLabel ?location ( AVG (?value ) AS ?avg ) ( MAX (?time ) AS ?maxTime ) " +
    " ( MIN (?time ) AS ?minTime ) " +
    "WHERE { " +
    "_:b0 sosa:isObservedBy ?sensor ; " +
    "sosa:hasSimpleResult ?value ; " +
    "sosa:madeBySensor ?sensorLabel ; " +
    "rdg:isLocatedIn ?location ; " +
    "dul:isObservableAt ?time . " +
    "} " +
    "GROUP BY ?sensor  ?sensorLabel ?location" +
    "} " +
    "}"


  var REQ_MOTION: String = "REGISTER STREAM cleangenerique AS " +
    "PREFIX :<http://ecareathome.org/stream#> " +
    "PREFIX rdf:<http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
    "PREFIX xsd:<http://www.w3.org/2001/XMLSchema#> " +
    "PREFIX dul:<http://www.ontologydesignpatterns.org/ont/dul/DUL.owl#> " +
    "PREFIX rdg:<http://users.abo.fi/ndiaz/public/HumanActivity.owl#> " +
    "PREFIX sosa: <http://www.w3.org/ns/sosa/> " +
    "CONSTRUCT " +
    "{ " +
    "_:c0 dul:isObservableAt _:c1 . " +
    "_:c0 sosa:isObservedBy ?sensor . " +
    "_:c0 sosa:hasSimpleResult ?value . " +
    "_:c0 sosa:madeBySensor ?sensorLabel ." +
    "_:c0 rdg:isLocatedIn ?location ." +
    "_:c1 rdf:type dul:TimeInterval . " +
    "_:c1 rdg:hasStartDatetime  ?minTime . " +
    "_:c1 rdg:hasEndDatetime  ?maxTime . " +
    "} " +
    "FROM STREAM <http://ecareathome.org/stream#generique> [ RANGE 10s STEP 10s] " +
    "FROM <http://www.w3.org/ns/sosa> " +
    "WHERE " +
    "{ { SELECT ?sensor ?sensorLabel ?location ?value ( MAX (?time ) AS ?maxTime ) " +
    " ( MIN (?time ) AS ?minTime ) " +
    "WHERE { " +
    "_:b0 sosa:isObservedBy ?sensor ; " +
    //"sosa:hasSimpleResult ?value ; " +
    "sosa:madeBySensor ?sensorLabel ; " +
    "rdg:isLocatedIn ?location ; " +
    "dul:isObservableAt ?time . " +
    "{ SELECT ?value WHERE { " +
    "_:b1 sosa:isObservedBy ?sensor ; " +
    "sosa:hasSimpleResult ?value ; " +
    "dul:isObservableAt ?timeend . " +
    " } " +
    "ORDER BY DESC(?timeend) limit 1 " +
    " } " +
    "} " +
    "GROUP BY ?sensor  ?sensorLabel ?location ?value" +
    "} "+
    "}" ;

}
