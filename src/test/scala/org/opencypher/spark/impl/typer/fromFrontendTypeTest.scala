package org.opencypher.spark.impl.typer

import org.neo4j.cypher.internal.frontend.v3_2.{symbols => frontend}
import org.opencypher.spark.BaseTestSuite
import org.opencypher.spark.api.types.{CTBoolean, CTFloat, CTInteger, CTNumber}

class fromFrontendTypeTest extends BaseTestSuite {

  test("should convert basic types") {
    fromFrontendType(frontend.CTBoolean) shouldBe CTBoolean
    fromFrontendType(frontend.CTInteger) shouldBe CTInteger
    fromFrontendType(frontend.CTFloat) shouldBe CTFloat
    fromFrontendType(frontend.CTNumber) shouldBe CTNumber
  }
}
