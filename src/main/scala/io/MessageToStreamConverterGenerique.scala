package io

import java.io.Serializable
import java.util
import java.util.concurrent.ArrayBlockingQueue

import com.hp.hpl.jena.graph.Node
import com.hp.hpl.jena.rdf.model.{RDFNode, Statement}
import common.ProfilingSMA
import edu.telecom.stet.cep.events.{GraphEvent, MappedEvent}
import edu.telecomstet.cep.dictionary.optimised.DictionaryOpImpl
import eu.larkc.csparql.cep.api.{RdfQuadruple, RdfStream}
import org.deri.cqels.engine.ExecContext
import org.semanticweb.yars.nx.parser.NxParser
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._

abstract class MessageToStreamConverterGenerique(iri: String) extends RdfStream(iri) {
  private val logger = LoggerFactory.getLogger(classOf[MessageToStreamConverterGenerique])
  private val profiling = new ProfilingSMA

  def MessageToStreamConverterGenerique(iri: String): Unit ={
    profiling.resetProfiling();
  }

  def convertMsg(arg: Serializable): Unit = {

    val stmts: util.List[Statement] = this.getStatements(arg)
    for (st <- stmts) {
      try {
        logger.debug(this.getIRI + " Streaming: " + st.toString)
        val q: RdfQuadruple = new RdfQuadruple(st.getSubject.toString, st.getPredicate.toString, st.getObject.toString, System.currentTimeMillis)
        this.put(q)
        profiling.numberOfEvents += 1
        logger.debug(this.getIRI + " after Streaming: " + q.toString)
      } catch {
        case e: Exception =>
          e.printStackTrace()
          logger.error(this.getIRI + " CSPARQL streamming error.")
      }
      // messageByte += st.toString().getBytes().length;
    }

  }

  def convertMsg(arg: Serializable, execContext: ExecContext): Unit = {
    val stmts: util.List[Statement] = this.getStatements(arg)

    for (st <- stmts) {
      try {
        logger.debug(this.getIRI + " Streaming: " + st.toString)
        //val q: RdfQuadruple = new RdfQuadruple(st.getSubject.toString, st.getPredicate.toString, st.getObject.toString, System.currentTimeMillis)
        //profiling.numberOfEvents += 1
        execContext.engine().send(Node.createURI(this.getIRI), st.getSubject.asNode(), st.getPredicate.asNode(), st.getObject.asNode());
        //logger.debug(this.getIRI + " Streaming: " + q.toString)
      } catch {
        case e: Exception =>
          e.printStackTrace()
          logger.error(this.getIRI + " CSPARQL streamming error.")
      }
      // messageByte += st.toString().getBytes().length;
    }
  }

  def getStatements(arg: Serializable): util.List[Statement]

  def convertMsg(data: Serializable, queue:  ArrayBlockingQueue[GraphEvent], dicImpl: DictionaryOpImpl, sostr: Long): Unit = {
    val stmts: util.List[Statement] = this.getStatements(data)
    var mapE = new Array[MappedEvent](stmts.size())
    var i = 0
    for (st <- stmts) {
      logger.debug(" Streaming: " + st.toString)
      //val q: RdfQuadruple = new RdfQuadruple(st.getSubject.toString, st.getPredicate.toString, st.getObject.toString, System.currentTimeMillis)
      var obj = toN3(st.getObject)
      var suj = toN3(st.getSubject)


      var line =  suj + " <" + st.getPredicate + "> " + obj + " ."
      logger.debug("Spaseq line in => {}", line)

      var node = NxParser.parseNodes(line)
      mapE(i) = new MappedEvent(node, dicImpl)
      i = i+1

      if(st.getObject.toString.contains("0.0073937153419593345")){
        queue.put(new GraphEvent(-1, 0L, null, 0L, new Array[MappedEvent](0)));
      }
    }

    var gr = new GraphEvent(1, System.nanoTime() - sostr, dicImpl, 1, mapE);
    queue.put(gr)

      // messageByte += st.toString().getBytes().length;
  }

  def toN3(st : RDFNode ): String = {
    var res = ""
    if (st.isURIResource) {
      res = "<" + st.toString + ">"
    }
    else if(st.isLiteral) {
      res = "\"" + st.toString.replace("^^", "\"^^<") + ">"
    }
    else {
      res = "_:"+st
    }
    res
  }

  def profilingData(): ProfilingSMA ={
    profiling
  }
}