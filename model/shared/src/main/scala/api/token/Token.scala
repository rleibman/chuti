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

package api.token

enum TokenPurpose(override val toString: String) {

  case NewUser extends TokenPurpose(toString = "NewUser")
  case LostPassword extends TokenPurpose(toString = "LostPassword")

}


case class Token(tok: String) {

  override def toString: String = tok

}
