select ?foi ?p1 ?foi2 ?p2

WITHIN 10 seconds
 
 from stream S1 <http://www.insight-centre.org/dataset/SampleEventService#Q7>
 from stream S2 <http://www.insight-centre.org/dataset/SampleEventService#AarhusParkingData0>
where 
 SEQ(A; B; C)

{
 DEFINE GPM A ON S1 {
?p1 <http://purl.oclc.org/NET/ssnx/ssn#observedProperty> ?foi .
}


 DEFINE GPM B ON S1 {
?p2 <http://purl.oclc.org/NET/ssnx/ssn#observedProperty> ?foi2 .
}

 DEFINE GPM C ON S2 {
?p3 <http://purl.oclc.org/NET/ssnx/ssn#observedProperty> ?foi3 .
}

}