package org.opencypher.spark.prototype.impl.planner

import org.apache.spark.sql.DataFrame
import org.opencypher.spark.prototype.api.expr._
import org.opencypher.spark.prototype.api.ir.QueryModel
import org.opencypher.spark.prototype.api.ir.global.{GlobalsRegistry, LabelRef}
import org.opencypher.spark.prototype.api.ir.pattern.AllGiven
import org.opencypher.spark.prototype.api.record.{ProjectedSlotContent}
import org.opencypher.spark.prototype.api.spark.{SparkCypherGraph, SparkCypherRecords, SparkCypherView}
import org.opencypher.spark.prototype.impl.instances.spark.records._
import org.opencypher.spark.prototype.impl.logical
import org.opencypher.spark.prototype.impl.syntax.transform._

class PhysicalPlanner {

  def plan(logicalPlan: logical.LogicalOperator)(implicit context: PhysicalPlanningContext): SparkCypherView = logicalPlan match {
    case logical.Select(fields, in) =>
      PhysicalSelect(fields.toMap, plan(in))
//      plan(in)
//      val frame = planOp(in).selectFields(fields.map(t => Symbol(t._2.replaceAllLiterally(".", "_"))): _*)
//      frame
    case logical.NodeScan(v, every) =>
      AllNodesScan(v, context.graph)
//      labelScan(v)(every.labels.elts.map(globals.label(_).name).toIndexedSeq).asProduct

    case logical.Project(it, in) =>
      PhysicalProject(it, plan(in))

    case logical.Filter(expr, in) => expr match {
      case HasLabel(n: Var, ref) =>
        LabelFilter(n, AllGiven(Set(ref)), plan(in))
      case _ => ???
    }
      //      if (in.signature.items.exists(_.exprs.contains(expr))) {
      //        planExpr(planOp(in), expr)
      //      }
//      planExpr(planOp(in), expr)
    case logical.ExpandSource(source, rel, target, in) =>
      // TODO: where is the rel-type info?
//      val rels = allRelationships(rel).asProduct
//        .relationshipStartId(rel)(relStart(rel))
//        .relationshipEndId(rel)(relEnd(rel))
//        .asRow
//      // TODO: where is the node label info?
//      val rhs = allNodes(target).asProduct.nodeId(target)(nodeId(target)).asRow
//      val lhs = planOp(in).nodeId(source)(nodeId(source))
//
//      lhs.asRow.join(rels).on(nodeId(source))(relStart(rel)).join(rhs).on(relEnd(rel))(nodeId(target)).asProduct
      ???
    case x => throw new NotImplementedError(s"Can't plan operator $x yet")
  }

}

case class AllNodesScan(node: Var, domain: SparkCypherGraph) extends SparkCypherView {
  override def graph: SparkCypherGraph = ???
  override def records: SparkCypherRecords = {
//    val nodes = domain.nodes.records
//    val newHeader = nodes.header.slots.map { r =>
//      val newExpr = r.expr match {
//        case _: Var => node
//        case p: Property => p.copy(m = node)
//        case l: HasLabel => l.copy(node = node)
//        case _ => ???
//      }
//
//      RecordSlot(ExprSlotKey(newExpr), r.cypherType)
//    }

    new SparkCypherRecords with Serializable {
      override def data: DataFrame = ??? // nodes.data
      override def header = ??? // RecordsHeader.from(newHeader)
    }
  }

  override def model: QueryModel[Expr] = ???
}

case class LabelFilter(node: Var, labels: AllGiven[LabelRef], in: SparkCypherView) extends SparkCypherView {

  override def domain: SparkCypherGraph = in.domain
  override def graph: SparkCypherGraph = ???
  override def records: SparkCypherRecords = {
    val labelExprs: Set[Expr] = labels.elts.map { ref => HasLabel(node, ref) }
    in.records.filter(Ands(labelExprs))
  }

  override def model: QueryModel[Expr] = ???
}

case class PhysicalSelect(fields: Map[Expr, String], in: SparkCypherView) extends SparkCypherView {
  override def domain: SparkCypherGraph = in.domain
  override def graph: SparkCypherGraph = ???

  override def records: SparkCypherRecords = {
    val records = in.records
    records.select(fields)
  }

  override def model: QueryModel[Expr] = ???
}

case class PhysicalProject(it: ProjectedSlotContent, in: SparkCypherView) extends SparkCypherView {
  override def domain: SparkCypherGraph = in.domain
  override def graph: SparkCypherGraph = ???

  override def records: SparkCypherRecords = {
    val records = in.records
    records.project(it)
  }

  override def model: QueryModel[Expr] = ???
}

case class PhysicalPlanningContext(graph: SparkCypherGraph, globals: GlobalsRegistry)