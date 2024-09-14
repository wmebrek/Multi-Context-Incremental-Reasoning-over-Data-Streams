select ?foi ?p1 ?foi2 ?p2

WITHIN 10 seconds
 
 from stream S1 <http://www.insight-centre.org/dataset/SampleEventService#Query2>
where 
 SEQ(A; B)

{
 DEFINE GPM A ON S1 {
?p1 <http://purl.oclc.org/NET/ssnx/ssn#observedProperty> ?foi .
}


 DEFINE GPM B ON S1 {
?p2 <http://purl.oclc.org/NET/ssnx/ssn#observedProperty> ?foi2 .
}

}