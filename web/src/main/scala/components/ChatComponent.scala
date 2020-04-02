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

package components

import java.time.{Instant, LocalDateTime, ZoneId}

import japgolly.scalajs.react.{BackendScope, _}
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._

case class User(name: String) //TODO move this to shared
case class ChatMessage(
  user: User,
  msg:  String,
  date: Long
) //TODO move this to shared

object ChatComponent {
  case class State(
    chatMessages: Seq[ChatMessage] = Seq.empty,
    user:         User = User(""),
    msgInFlux:    String = ""
  )
  class Backend($ : BackendScope[_, State]) {
    def onUserChange(e: ReactEventFromInput): Callback = {
      val newVal = e.target.value
      $.modState(_.copy(user = User(newVal)))
    }

    def onMessageInFluxChange(e: ReactEventFromInput): Callback = {
      val newVal = e.target.value
      $.modState(_.copy(msgInFlux = newVal))
    }

    def onSend(s: State): Callback = {
      val msg = ChatMessage(s.user, s.msgInFlux, System.currentTimeMillis())
      Callback.log(s"Sending msg = $msg!") >> $.modState(_.copy(msgInFlux = ""))
    }

    def render(s: State): VdomElement =
      <.div(
        "User",
        <.input.text(^.value := s.user.name, ^.onChange ==> onUserChange),
        <.h1("Messages"),
        <.table(
          <.tbody(
            s.chatMessages.toVdomArray(msg =>
              <.tr(
                <.td(
                  LocalDateTime
                    .ofInstant(Instant.ofEpochMilli(msg.date), ZoneId.systemDefault())
                    .toString
                ),
                <.td(msg.user.name),
                <.td(msg.msg)
              )
            )
          )
        ),
        "Message",
        <.textarea(^.value := s.msgInFlux, ^.onChange ==> onMessageInFluxChange),
        <.button(^.onClick --> onSend(s), "Send")
      )

    def refresh(s: State): Callback = Callback.empty //TODO add ajax initalization stuff here
  }

  private val component = ScalaComponent
    .builder[Unit]("content")
    .initialState(State())
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.refresh($.state))
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()

}
