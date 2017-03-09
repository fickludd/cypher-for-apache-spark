package org.opencypher.spark.prototype.impl.convert

import org.neo4j.cypher.internal.frontend.v3_2.ast
import org.opencypher.spark.prototype.api.ir.global._

object GlobalsExtractor {

  def apply(expr: ast.ASTNode, tokens: GlobalsRegistry = GlobalsRegistry.none): GlobalsRegistry = {
    expr.fold(tokens) {
      case ast.LabelName(name) => _.withLabel(Label(name))
      case ast.RelTypeName(name) => _.withRelType(RelType(name))
      case ast.PropertyKeyName(name) => _.withPropertyKey(PropertyKey(name))
      case ast.Parameter(name, _) => _.withConstant(Constant(name))
    }
  }
}