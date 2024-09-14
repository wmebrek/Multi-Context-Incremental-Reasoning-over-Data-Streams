SELECT ?p1 ?v1 ?p2 ?v2
WITHIN 10 SECONDS
FROM stream S1 <http://www.insight-centre.org/dataset/SampleEventService#AarhusTrafficData0>
 
WHERE
 SEQ(A; B)
 
 { 
 DEFINE GPM A ON S1 {
 ?traffic <http://purl.oclc.org/NET/ssnx/ssn#observedProperty> ?p1 .
 ?traffic <http://purl.oclc.org/NET/sao/hasValue> ?v1 .
 }

 DEFINE GPM B ON S1 {
 ?traffic2 <http://purl.oclc.org/NET/ssnx/ssn#observedProperty> ?p2 .
 ?traffic2 <http://purl.oclc.org/NET/sao/hasValue> ?v2 .
 }
 
}