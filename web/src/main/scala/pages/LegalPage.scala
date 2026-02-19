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

package pages

import japgolly.scalajs.react.*
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^.*

object LegalPage extends ChutiPage {

  case class State()

  class Backend($ : BackendScope[Unit, State]) {

    def render(): VdomElement = {
      <.div(
        ^.className := "legal-page",
        <.h1("Aviso Legal / Legal Notice"),
        <.h2("Terms of Service"),
        <.p(
          "By accessing and using chuti.fun (the \"Site\"), you agree to be bound by the following terms and conditions. ",
          "If you do not agree with any part of these terms, you must not use this Site."
        ),
        <.h2("Not a Gambling Site"),
        <.p(
          "Chuti.fun is a free online domino game provided solely for entertainment and recreational purposes. ",
          "This Site is ",
          <.strong("NOT"),
          " a gambling site. No real money, cryptocurrency, or anything of monetary value ",
          "is wagered, won, lost, or transacted through this Site. Any in-game points, scores, or virtual currencies ",
          "(such as \"satoshi\" references) are purely fictional, have no real-world value, and cannot be exchanged, ",
          "redeemed, or converted into real money or any other form of consideration."
        ),
        <.p(
          "No financial transactions of any kind are processed through this Site. Users do not pay to play, ",
          "and there are no purchases, subscriptions, or fees associated with the use of this Site."
        ),
        <.h2("Fair Play and Randomness"),
        <.p(
          "We are committed to providing a fair gaming experience for all players. We take the following measures ",
          "to ensure fairness:"
        ),
        <.ul(
          <.li(
            <.strong("Tile Shuffling: "),
            "Domino tiles are shuffled using cryptographically secure random number generation provided by the ",
            "Java SecureRandom library. This ensures that tile distribution is truly random and unpredictable, ",
            "and that no player or operator can influence or predict the outcome of the shuffle."
          ),
          <.li(
            <.strong("Equal Treatment: "),
            "All players are subject to the same rules and game mechanics. No player receives preferential ",
            "treatment or advantages of any kind."
          ),
          <.li(
            <.strong("Hidden Information: "),
            "Each player can only see their own tiles. The server enforces information barriers so that no ",
            "player can access another player's hand or any other hidden game state."
          ),
          <.li(
            <.strong("Bot Transparency: "),
            "When computer-controlled players (bots) are present in a game, they are clearly identified as such. ",
            "Bots follow the same game rules as human players and do not have access to hidden information ",
            "(such as other players' tiles)."
          )
        ),
        <.h2("No Warranty"),
        <.p(
          "This Site and its services are provided \"as is\" and \"as available\" without warranties of any kind, ",
          "either express or implied, including but not limited to warranties of merchantability, fitness for a ",
          "particular purpose, or non-infringement. We do not warrant that the Site will be uninterrupted, ",
          "error-free, or free of viruses or other harmful components."
        ),
        <.h2("Limitation of Liability"),
        <.p(
          "To the fullest extent permitted by applicable law, chuti.fun, its owner, operators, and contributors ",
          "shall not be liable for any direct, indirect, incidental, special, consequential, or exemplary damages ",
          "arising out of or in connection with the use of this Site, even if advised of the possibility of such damages."
        ),
        <.h2("User Conduct"),
        <.p("By using this Site, you agree to:"),
        <.ul(
          <.li("Use the Site only for lawful purposes and in accordance with these terms."),
          <.li("Not attempt to disrupt, manipulate, or interfere with the normal operation of the Site or its games."),
          <.li("Not use automated tools, scripts, or bots to interact with the Site without authorization."),
          <.li("Not harass, abuse, or threaten other users through the Site's chat or other communication features."),
          <.li("Not attempt to gain unauthorized access to the Site, its servers, or any associated systems.")
        ),
        <.h2("Privacy"),
        <.p(
          "We collect only the minimum information necessary to operate the Site, including your email address ",
          "and display name for account purposes. We do not sell, share, or distribute your personal information ",
          "to third parties. Game data and chat messages are stored on our servers for the purpose of operating ",
          "the game and may be deleted at any time without notice."
        ),
        <.h2("Intellectual Property"),
        <.p(
          "The chuti.fun software is open source and licensed under the Apache License, Version 2.0. ",
          "The game of Chuti (dominoes) is a traditional game and is not subject to copyright. ",
          "User-generated content (such as chat messages) remains the property of the respective users."
        ),
        <.h2("Age Requirement"),
        <.p(
          "This Site is intended for users aged 13 and older. By using this Site, you represent that you are ",
          "at least 13 years of age. If you are under 13, you may not use this Site."
        ),
        <.h2("Modifications"),
        <.p(
          "We reserve the right to modify these terms at any time without prior notice. Your continued use of ",
          "the Site after any changes constitutes acceptance of the new terms. It is your responsibility to review ",
          "these terms periodically."
        ),
        <.h2("Governing Law"),
        <.p(
          "These terms shall be governed by and construed in accordance with the laws of the United States. ",
          "Any disputes arising from the use of this Site shall be resolved in the appropriate courts of that jurisdiction."
        ),
        <.h2("Contact"),
        <.p(
          "If you have any questions about these terms or the Site, please contact us through the project's ",
          "GitHub repository."
        ),
        <.p(
          ^.fontStyle := "italic",
          "Last updated: February 2026"
        )
      )
    }

  }

  private val component = ScalaComponent
    .builder[Unit]
    .initialState(State())
    .backend[Backend](Backend(_))
    .render(_.backend.render())
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()

}
