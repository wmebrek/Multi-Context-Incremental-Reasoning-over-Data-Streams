 prefix c: <http://example/company#> 
                        
                        prefix pred: <http://example/>
                        
                       

                        SELECT ?company ?p1 ?p2 ?p3 ?p4
                         WITHIN 10 SECONDS 
                 FROM STREAM S1 <http://example.org/main> 
               
                        WHERE 
                         SEQ (A, (B|C), D)
                           {
       DEFINE GPM A ON S1
                 { 
                  ?company pred:price ?p1.
                  ?company pred:volume ?vol1. 
                  } 
                      
                             
         DEFINE GPM B ON S1
                  {
 					?company2 pred:price ?p2.
					?company2 pred:volume ?vol2. 
					Filter (?p2 < ?p1 ).
                   }

           DEFINE GPM C ON S1
                  {
                  ?company3 pred:price ?p3.
                  ?company3 pred:volume ?vol3. 
                  Filter (?p3 = '16.0'^^<http://www.w3.org/2001/XMLSchema#double>).
                  }
           
           DEFINE GPM D ON S1 
                  {
                  ?company4 pred:price ?p4.
                  ?company4 pred:volume ?vol4. 
                  Filter (?p4 > ?p1 ).
                  }
              
                     
              }