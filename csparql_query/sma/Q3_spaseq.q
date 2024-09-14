select ?congestion

WITHIN 10 seconds
 
 from stream S1 <http://www.insight-centre.org/dataset/SampleEventService#AarhusTrafficData0> 
 
where 
 SEQ(A)

{
 DEFINE GPM A ON S1 {
?p1 <http://purl.oclc.org/NET/ssnx/ssn#isPropertyOf> ?foi.
?foi <http://www.insight-centre.org/citytraffic#hasStartLatitude> ?lat1.
?foi <http://www.insight-centre.org/citytraffic#hasStartLongitude> ?lng1.
?foi <http://www.insight-centre.org/citytraffic#hasEndLatitude> ?lat2.
?foi <http://www.insight-centre.org/citytraffic#hasEndLongitude> ?lng2.
?obId1 <http://purl.oclc.org/NET/ssnx/ssn#observedProperty> ?p1.
?obId1 <http://purl.oclc.org/NET/sao/hasValue> ?congestion.

}

}