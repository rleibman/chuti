/*
 * Copyright 2020 Roberto Leibman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package caliban.client.scalajs

import caliban.client.CalibanClientError.DecodingError
import caliban.client.{ArgEncoder, ScalarDecoder, __Value}
import caliban.client.__Value.__ObjectValue
import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, writeToString}
import io.circe.Json

import java.time.LocalDateTime
import scala.util.Try


given ScalarDecoder[Json] = {
  case input: __ObjectValue => io.circe.parser.parse(writeToString(input)).left.map(e => DecodingError(e.message, Option(e)))
  case _ => Left(DecodingError("Expected an object"))
}
given ScalarDecoder[LocalDateTime] = {
  case __Value.__StringValue(value) =>
    Try(LocalDateTime.parse(value)).toEither.left.map(e => DecodingError("Error parsing date", Option(e)))
  case _ => throw DecodingError("Expected a string")
}

given ArgEncoder[Json] = { (json: Json) =>
  ArgEncoder.json.encode {
    readFromString[__ObjectValue](json.noSpaces)
  }
}
