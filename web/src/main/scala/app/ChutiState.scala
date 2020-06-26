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

package app

import chuti.User
import japgolly.scalajs.react.React.Context
import japgolly.scalajs.react.{Callback, React}

case class ChutiState(
  onUserChanged:              Option[Option[User] => Callback] = None,
  user:                       Option[User] = None,
  addPageMenuItemListener:    (() => Seq[(String, Callback)]) => Callback =  _ => Callback.empty,
  removePageMenuItemListener: (() => Seq[(String, Callback)]) => Callback = _ => Callback.empty,
  pageMenuItemListeners:      Seq[() => Seq[(String, Callback)]] = Seq.empty,
//  pageMenuItems:          Seq[(String, Callback)] = Seq.empty,
//  onPageMenuItemsChanged: Option[Seq[(String, Callback)] => Callback] = None,
  serverVersion: Option[String] = None
) {}

object ChutiState {
  val ctx: Context[ChutiState] = React.createContext(ChutiState())
}
