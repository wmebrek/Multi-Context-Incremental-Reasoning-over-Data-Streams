select ?foi ?p1 ?foi2 ?p2

WITHIN 10 seconds
 
 from stream S1 <http://localhost:0000#Q5> 
 
where 
 SEQ(A; B)

{
 DEFINE GPM A ON S1 {
?p1 <http://purl.oclc.org/NET/ssnx/ssn#isPropertyOf> ?foi.
}


 DEFINE GPM B ON S1 {
?p2 <http://purl.oclc.org/NET/ssnx/ssn#isPropertyOf> ?foi2.
}

}