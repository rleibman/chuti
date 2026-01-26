sbt "project server" \
"calibanGenClient /home/rleibman/projects/chuti/server/src/main/graphql/game.gql /home/rleibman/projects/chuti/web/src/main/scala/caliban/client/scalajs/GameClient.scala --genView true --scalarMappings Json:zio.json.ast.Json,LocalDateTime:java.time.LocalDateTime --packageName caliban.client.scalajs" \
"calibanGenClient /home/rleibman/projects/chuti/server/src/main/graphql/chat.gql /home/rleibman/projects/chuti/web/src/main/scala/caliban/client/scalajs/ChatClient.scala --genView true --scalarMappings Json:zio.json.ast.Json,LocalDateTime:java.time.LocalDateTime --packageName caliban.client.scalajs" \

