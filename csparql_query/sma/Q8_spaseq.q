prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
select ?congestion ?congestion2

WITHIN 10 seconds
 
 from stream S1 <http://www.insight-centre.org/dataset/SampleEventService#Q8>
where 
 SEQ(A; B)

{
 DEFINE GPM A ON S1 {
?p1 rdf:type ?type .
?p1 <http://purl.oclc.org/NET/sao/hasValue> ?congestion .
?p1 <http://purl.oclc.org/NET/ssnx/ssn#observedProperty> ?co .
}

 DEFINE GPM B ON S1 {
?p2 rdf:type ?type .
?p2 <http://purl.oclc.org/NET/sao/hasValue> ?congestion2 .
?p2 <http://purl.oclc.org/NET/ssnx/ssn#observedProperty> ?co2 .
filter(?congestion > ?congestion2)
}




}
