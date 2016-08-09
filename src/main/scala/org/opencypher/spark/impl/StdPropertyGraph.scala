package org.opencypher.spark.impl

import org.apache.spark.sql._
import org.apache.spark.sql.catalyst.encoders.ExpressionEncoder
import org.apache.spark.sql.types.StringType
import org.opencypher.spark.CypherTypes.{CTString, CTAny, CTNode}
import org.opencypher.spark._
import org.opencypher.spark.impl.frame._
import org.opencypher.spark.impl.util.SlotSymbolGenerator

import scala.collection.immutable.ListMap
import scala.language.implicitConversions

object StdPropertyGraph {

  object SupportedQueries {
    val allNodesScan = "MATCH (n) RETURN (n)"
    val allNodesScanProjectAgeName = "MATCH (n) RETURN n.name AS name, n.age AS age"
    val allNodeIds = "MATCH (n) RETURN id(n)"
    val allNodeIdsSortedDesc = "MATCH (n) RETURN id(n) AS id ORDER BY id DESC"
    val getAllRelationshipsOfTypeT = "MATCH ()-[r:T]->() RETURN r"
    val getAllRelationshipsOfTypeTOfLabelA = "MATCH (:A)-[r]->(:B) RETURN r"
    val simpleUnionAll = "MATCH (a:A) RETURN a.name AS name UNION ALL MATCH (b:B) RETURN b.name AS name"
    val simpleUnionDistinct = "MATCH (a:A) RETURN a.name AS name UNION MATCH (b:B) RETURN b.name AS name"
    val optionalMatch = "MATCH (a:A) OPTIONAL MATCH (a)-[r]->(b) RETURN r"
    val unwind = "WITH [1, 2, 3] AS l UNWIND l AS x RETURN x"
    val matchAggregateAndUnwind = "MATCH (a:A) WITH collect(a.name) AS names UNWIND names AS name RETURN name"
    val shortestPath = "MATCH (a {name: 'Ava'}), (b {name: 'Sasha'}) MATCH p=shortestPath((a)-->(b)) RETURN p"
    // not implemented
    val boundVarLength = "MATCH (a:A)-[r*2]->(b:B) RETURN r"
  }

}

class StdPropertyGraph(nodes: Dataset[CypherNode], relationships: Dataset[CypherRelationship])
                      (implicit private val session: SparkSession) extends PropertyGraph {

  import StdPropertyGraph.SupportedQueries
  import session.implicits._

  private implicit def stringFromSymbol(s: Symbol): String = s.name

  override def cypher(query: String): CypherResult[Row] = {
    val slotNames = new SlotSymbolGenerator
    implicit val planningContext = new PlanningContext(slotNames)
    implicit val runtimeContext = new StdRuntimeContext(session)

    query match {

      case SupportedQueries.allNodesScan =>
        val nodeFrame = CypherNodes(nodes)('n)
        val rowFrame = CypherValuesAsRows(nodeFrame)
        StdRowResult(rowFrame)

      case SupportedQueries.allNodesScanProjectAgeName =>
        val nodeFrame = CypherNodes(nodes)('n)
        val rowFrame = CypherValuesAsRows(nodeFrame)
        val productFrame = RowsAsProducts(rowFrame)
        val projectFrame1 = PropertyAccessProducts(productFrame, 'n, 'name)(StdField(Symbol("n.name"), CTAny.nullable))
        val projectFrame2 = PropertyAccessProducts(projectFrame1, 'n, 'age)(StdField(Symbol("n.age"), CTAny.nullable))
        val rows = ProductsAsRows(projectFrame2)

        StdRowResult(rows)
//
//        val field1 = StdField('name, CTAny)
//        val field2 = StdField('age, CTAny)
//        val slot1 = StdSlot(slotNames.newSlotName(field1.name), CTString, EmbeddedRepresentation(StringType))
//        val slot2 = StdSlot(slotNames.newSlotName(field2.name), CTAny, BinaryRepresentation)
//
//        new StdCypherFrame(Seq(field1, field2), Map(field1.name -> slot1, field2.name -> slot2)) {
//          override protected def execute: DataFrame = {
//
//            import CypherValue.{implicits => encoders}
//
//            val df0 = nodes.toDF("n")
//            val encoder0 = ExpressionEncoder.tuple(Seq(encoders.cypherValueEncoder[CypherNode])).asInstanceOf[ExpressionEncoder[Product]]
//            val ds0 = df0.as[Product](encoder0)
//
//            val encoder1 = productEncoder(slots.values.toSeq)
//            val ds1 = ds0.map(WorkItem(fields))(encoder1)
//            val df1 = ds1.toDF(slots.values.map(_.name.name).toSeq: _*)
//
//            df1
//          }
//        }.result

//      case SupportedQueries.allNodeIds =>
//        new StdFrame(nodes.map[StdRecord] { node: CypherNode =>
//          StdRecord(Array(CypherInteger(node.id.v)), Array.empty)
//        }, ListMap("value" -> 0)).result
//
//      case SupportedQueries.allNodeIdsSortedDesc =>
//        new StdFrame(session.createDataset(nodes.map[StdRecord] { node: CypherNode =>
//          StdRecord(Array(CypherInteger(node.id.v)), Array.empty)
//        }.rdd.sortBy[Long]({ record =>
//          record.values(0).asInstanceOf[CypherInteger].v
//        }, false)), ListMap("value" -> 0)).result
//
//      case SupportedQueries.getAllRelationshipsOfTypeT =>
//        new StdFrame(relationships.filter(_.typ == "T").map(r => StdRecord(Array(r), Array.empty)), ListMap("r" -> 0)).result
//
//      case SupportedQueries.getAllRelationshipsOfTypeTOfLabelA =>
//        val a = nodes.filter(_.labels.contains("A")).map(node => (node.id.v, node))(Encoders.tuple(implicitly[Encoder[Long]], CypherValue.implicits.cypherValueEncoder[CypherNode])).toDF("id_a", "val_a")
//        val b = nodes.filter(_.labels.contains("B")).map(node => (node.id.v, node))(Encoders.tuple(implicitly[Encoder[Long]], CypherValue.implicits.cypherValueEncoder[CypherNode])).toDF("id_b", "val_b")
//        val rels = relationships.map(rel => (rel.start.v, rel.end.v, rel.id.v, rel))(Encoders.tuple(implicitly[Encoder[Long]], implicitly[Encoder[Long]], implicitly[Encoder[Long]], CypherValue.implicits.cypherValueEncoder[CypherRelationship])).toDF("start_rel", "end_rel", "id_rel", "val_rel")
//        val joined =
//          rels
//            .join(a, functions.expr("id_a = start_rel"))
//            .join(b, functions.expr("id_b = end_rel"))
//            .select(new Column("val_rel").as("value"))
//        val result = joined.as[CypherRelationship](CypherValue.implicits.cypherValueEncoder[CypherRelationship])
//
//        new StdFrame(result.map(r => StdRecord(Array(r), Array.empty)), ListMap("r" -> 0)).result
//
//
//      case SupportedQueries.optionalMatch =>
//        val lhs = nodes.filter(_.labels.contains("A")).map(node => (node.id.v, node))(Encoders.tuple(implicitly[Encoder[Long]], CypherValue.implicits.cypherValueEncoder[CypherNode])).toDF("id_a", "val_a")
//
//        val b = nodes.filter(_.labels.contains("B")).map(node => (node.id.v, node))(Encoders.tuple(implicitly[Encoder[Long]], CypherValue.implicits.cypherValueEncoder[CypherNode])).toDF("id_b", "val_b")
//        val rels = relationships.map(rel => (rel.start.v, rel.end.v, rel.id.v, rel))(Encoders.tuple(implicitly[Encoder[Long]], implicitly[Encoder[Long]], implicitly[Encoder[Long]], CypherValue.implicits.cypherValueEncoder[CypherRelationship])).toDF("start_rel", "end_rel", "id_rel", "val_rel")
//        val rhs = rels.join(b, functions.expr("id_b = end_rel"))
//
//        val joined = lhs.join(rhs, functions.expr("id_a = start_rel"), "left_outer")
//
//        val rel = joined.select(new Column("val_rel").as("value"))
//        val result = rel.as[CypherRelationship](CypherValue.implicits.cypherValueEncoder[CypherRelationship])
//
//        new StdFrame(result.map(r => StdRecord(Array(r), Array.empty)), ListMap("r" -> 0)).result
//
//      case SupportedQueries.simpleUnionAll =>
//        val a = nodes.filter(_.labels.contains("A")).map(node => node.properties.getOrElse("name", CypherNull))(CypherValue.implicits.cypherValueEncoder[CypherValue]).toDF("name")
//        val b = nodes.filter(_.labels.contains("B")).map(node => node.properties.getOrElse("name", CypherNull))(CypherValue.implicits.cypherValueEncoder[CypherValue]).toDF("name")
//        val result = a.union(b).as[CypherValue](CypherValue.implicits.cypherValueEncoder[CypherValue])
//
//        new StdFrame(result.map(v => StdRecord(Array(v), Array.empty)), ListMap("name" -> 0)).result
//
//      case SupportedQueries.simpleUnionDistinct =>
//        val a = nodes.filter(_.labels.contains("A")).map(node => node.properties.getOrElse("name", CypherNull))(CypherValue.implicits.cypherValueEncoder[CypherValue]).toDF("name")
//        val b = nodes.filter(_.labels.contains("B")).map(node => node.properties.getOrElse("name", CypherNull))(CypherValue.implicits.cypherValueEncoder[CypherValue]).toDF("name")
//        val result = a.union(b).distinct().as[CypherValue](CypherValue.implicits.cypherValueEncoder[CypherValue])
//
//        new StdFrame(result.map(v => StdRecord(Array(v), Array.empty)), ListMap("name" -> 0)).result
//
//      case SupportedQueries.unwind =>
//        val l = CypherList(Seq(1, 2, 3).map(CypherInteger(_)))
//        val start = session.createDataset(Seq(l))(CypherValue.implicits.cypherValueEncoder[CypherList])
//
//        val result = start.flatMap(_.v)(CypherValue.implicits.cypherValueEncoder[CypherValue])
//
//        new StdFrame(result.map(v => StdRecord(Array(v), Array.empty)), ListMap("x" -> 0)).result
//
//      case SupportedQueries.matchAggregateAndUnwind =>
//        val lhs = nodes.filter(_.labels.contains("A")).flatMap(_.properties.get("name"))(CypherValue.implicits.cypherValueEncoder[CypherValue])
//
//        val collected = lhs.rdd.aggregate(List.empty[CypherValue])((ls, c) => ls :+ c, _ ++ _)
//        val result = session.createDataset(collected)(CypherValue.implicits.cypherValueEncoder[CypherValue])
//
//        new StdFrame(result.map(v => StdRecord(Array(v), Array.empty)), ListMap("name" -> 0)).result
//
//      case SupportedQueries.shortestPath =>
//        val a = nodes.flatMap(_.properties.get("name").filter(_ == "Ava"))(CypherValue.implicits.cypherValueEncoder[CypherValue])
//        val b = nodes.flatMap(_.properties.get("name").filter(_ == "Sasha"))(CypherValue.implicits.cypherValueEncoder[CypherValue])
//
//        ???
//
//      case SupportedQueries.boundVarLength =>
//        val a = nodes.filter(_.labels.contains("A")).map(node => (node.id.v, node))(Encoders.tuple(implicitly[Encoder[Long]], CypherValue.implicits.cypherValueEncoder[CypherNode])).toDF("id_a", "val_a")
//        val b = nodes.filter(_.labels.contains("B")).map(node => (node.id.v, node))(Encoders.tuple(implicitly[Encoder[Long]], CypherValue.implicits.cypherValueEncoder[CypherNode])).toDF("id_b", "val_b")
//        val rels1 = relationships.map(rel => (rel.start.v, rel.end.v, rel.id.v, rel))(Encoders.tuple(implicitly[Encoder[Long]], implicitly[Encoder[Long]], implicitly[Encoder[Long]], CypherValue.implicits.cypherValueEncoder[CypherRelationship])).toDF(
//          "start_rel1", "end_rel1", "id_rel1", "val_rel1"
//        )
//        val rels2 = rels1.select(
//          new Column("start_rel1").as("start_rel2"),
//          new Column("end_rel1").as("end_rel2"),
//          new Column("id_rel1").as("id_rel2"),
//          new Column("val_rel1").as("val_rel2")
//        )
//
//        val step1out = rels1.join(a, functions.expr("id_a = start_rel1"))
//        val step1done = step1out.join(b, functions.expr("end_rel1 = id_b"))
//
//        val prepare1 = step1done.select(new Column("val_rel1").as("r"))
//        val result1 = prepare1
//          .as[CypherRelationship](CypherValue.implicits.cypherValueEncoder[CypherRelationship])
//          .map(r => CypherList(Seq(r)))(CypherValue.implicits.cypherValueEncoder[CypherList])
//
//        val step2out = step1out.join(rels2, functions.expr("end_rel1 = start_rel2"))
//        val step2done = step2out.join(b, functions.expr("end_rel2 = id_b"))
//
//        val prepare2 = step2done.select(new Column("val_rel1").as("r1"), new Column("val_rel2").as("r2"))
//        val encoder2 = ExpressionEncoder.tuple(Seq(CypherValue.implicits.cypherValueEncoder[CypherRelationship], CypherValue.implicits.cypherValueEncoder[CypherRelationship]).map(_.asInstanceOf[ExpressionEncoder[_]])).asInstanceOf[Encoder[Product]]
//        val result2 = prepare2
//          .as[Product](encoder2)
//          .map(p => CypherList(p.productIterator.map(_.asInstanceOf[CypherRelationship]).toList))(CypherValue.implicits.cypherValueEncoder[CypherList])
//
//        val result = result1.union(result2)
//
//        new StdFrame(result.map(r => StdRecord(Array(r), Array.empty)), ListMap("r" -> 0)).result
//
//      // *** Functionality left to test
//
//      // [X] Scans (via df access)
//      // [X] Projection (via ds.map)
//      // [X] Predicate filter (via ds.map to boolean and ds.filter)
//      // [X] Expand (via df.join)
//      // [X] Union All
//      // [X] Union Distinct (spark distinct vs distinct on rdd's with explicit orderability)
//      // [X] Optional match (via df.join with type left_outer)
//      // [X] UNWIND
//      // [X] Bounded var length (via UNION and single steps)
//      // [X] Unbounded var length (we think we can do it in a loop but it will be probably be really expensive)
//      // [-] Shortest path -- not easily on datasets/dataframes directly but possible via graphx
//
//      // [X] Aggregation (via rdd, possibly via spark if applicable given the available types)
//      // [X] CALL .. YIELD ... (via df.map + procedure registry)
//      // [X] Graph algorithms (map into procedures)
//
//      /* Updates
//
//        ... 2 3 4 2 2 ... => MERGE (a:A {id: id ...}
//
//        ... | MERGE 2
//        ... | MERGE 3
//        ... | MERGE 4
//        ... | MERGE 2
//        ... | MERGE 2
//
//        ... | CREATE-MERGE 2
//        ... | MATCH-MERGE 3
//        ... | CREATE-MERGE 4
//        ... | CREATE-MERGE 2
//        ... | CREATE-MERGE 2
//
//     */
//
//      // CypherFrames and expression evaluation (including null issues)
      case _ =>
        ???
    }
  }
}