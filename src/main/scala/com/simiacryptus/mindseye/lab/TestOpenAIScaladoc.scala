package com.simiacryptus.mindseye.lab

import com.fasterxml.jackson.databind.{MapperFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.apache.commons.io.FileUtils
import org.apache.http.client.methods.{HttpGet, HttpPost, HttpRequestBase}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils

import java.io.File
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.meta.Term._

object TestOpenAIScaladoc {
  val root = new File("""C:\Users\andre\code\all-projects\deepartist\deepartist.org\""")
  val language = "scala"
  val version_class = 9
  val version_method = 9
  val apiBase = "https://api.openai.com/v1"
//  val model = "text-ada-001"
  val model = "text-davinci-002"

  lazy val engines = getMapper().readValue(getRequest(apiBase + "/engines"), classOf[Response]).data

  def main(args: Array[String]): Unit = {


    println("Root: " + root.getAbsolutePath)
    val files = FileUtils.listFiles(root, Array(language), true).asScala.toList
    println(s"Found ${files.size} files")
    for (file <- files) {
      println("File: " + file.getAbsolutePath)
      val data = FileUtils.readFileToString(file, "UTF-8")

      import scala.meta._
      val tree: Source = data.parse[Source].get
      val edits = new ArrayBuffer[(scala.Int, String)]()
      tree.traverse({

        case _class : Defn.Class =>
          val definition : String = _class.transform({
            case _template: Template => _template.copy(stats = List.empty)
          }).toString()

          val start = _class.pos.start
          if (getDocgenVersion(data, start).getOrElse(-1) < version_class) {
            println(s"Class Definition: $definition")
            val comment = testComment(definition, spaces(_class.pos.startColumn))
            edits ++= List((start, injectDocgenVersion(comment, version_class)))
          }

        case _method : Defn.Def if _method.paramss.size > 0 =>
          val definition : String = _method.transform({
            case _block: Block => _block.copy(stats = List.empty)
          }).toString()
          val start = _method.pos.start
          if (getDocgenVersion(data, start).getOrElse(-1) < version_method) {
            println(s"Method Definition: $definition")
            val info = if(definition.length < 64 && _method.toString().length < 512) {
              _method.toString()
            } else {
              definition
            }
            val comment = testComment(info, spaces(_method.pos.startColumn))
            edits ++= List((start, injectDocgenVersion(comment, version_method)))
          }
      })
      var workingData = data
      for((start, newComment) <- edits.sortBy(-_._1)) {
        val comment = preceedingComment(data, start)
        val oldCommentSize = comment.map(_.size).getOrElse(0)
        workingData = workingData.substring(0,start-oldCommentSize) + newComment + workingData.substring(start, workingData.length)
      }
      if(workingData != data) {
        FileUtils.write(file, workingData, "UTF-8")
      }

    }
  }

  def spaces(column: Int): String = {
    String.copyValueOf(Stream.continually(' ').take(column).toArray)
  }

  def injectDocgenVersion(comment: String, version: Int): String = {
    comment.replaceAll("""([\t ]*)\*/""", "$1*\n$1*   @docgenVersion " + version + "\n$1*/")
  }

  def getDocgenVersion(data: String, start: Int): Option[Int] = {
    preceedingComment(data, start).flatMap(comment => {
      """@docgenVersion\s+(\d+)""".r.findFirstMatchIn(comment).map(_.group(1).toInt)
    })
  }

  def testComment(codeData : String, indent : String) = {
    var documentation = "/**\n *"
    var prompt = "Translate this Scala into an English code comment:\n\nScala:\n\n" + codeData + "\n\nEnglish:\n\n" + documentation

    def advance() = {
      val response = getMapper().readValue(postRequest(apiBase + "/engines/" + model + "/completions", Map(
        "prompt" -> prompt,
        "temperature" -> 0,
        "max_tokens" -> 512,
        "stop" -> "*/"
      )), classOf[TextCompletion])
      require(response.error.isEmpty,response.error.get)
      var next = response.choices.head.text
        .replaceAllLiterally("\n", "\n" + indent)
        .replaceAll("\n{2,}", "\n")
      if(response.choices.head.finish_reason == "stop") next = next + "*/"
      documentation = documentation + next
      prompt = prompt + next
    }

    advance()
    while(!documentation.endsWith("*/")) {
      //println(documentation)
      val prod = "\n * "
      documentation = documentation.trim + prod
      prompt = prompt.trim + prod
      advance()
    }

    require(documentation.endsWith("*/"), "Completion did not contain valid comment: " + documentation)
    documentation = documentation.split('\n').map(_.trim).map(s=>if(s.startsWith("*")) " " + s else s).mkString("\n")
    println(documentation)
    indent + documentation + "\n" + indent
//    s"""$indent/*
//       |$indent * ${codeData.replaceAllLiterally("\n", s"\n$indent * ")}
//       |$indent */
//       |$indent""".stripMargin
  }

  def preceedingComment(data: String, start: Int) = {
    """(?s)^\s*/\*([^*]|\*(?!/))*\*/[ \t]*""".r.findFirstMatchIn(data.substring(0, start).reverse).map(_.group(0).reverse)
  }


  /** This scala function sends a POST request to the url with a json payload given by the map.
   *  It returns a json string.
   *
   *  @param url the url to send the request to
   *  @param map the json payload to send
   *  @return the json string returned by the request
   */
  def postRequest(url: String, map: Map[String, Any]): String = {
    val json = getMapper.writeValueAsString(map)
    val client = HttpClientBuilder.create().build()
    val request = new HttpPost(url)
    request.addHeader("Content-Type", "application/json")
    request.addHeader("Accept", "application/json")
    authorize(request)
    request.setEntity(new StringEntity(json))
    val response = client.execute(request)
    val entity = response.getEntity()
    EntityUtils.toString(entity)
  }

  def getMapper() = {
    val mapper = new ObjectMapper()
    mapper
      .enable(SerializationFeature.INDENT_OUTPUT)
      .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
      .enable(MapperFeature.USE_STD_BEAN_NAMING)
      .registerModule(DefaultScalaModule)
      .activateDefaultTyping(mapper.getPolymorphicTypeValidator())
    mapper
  }

  def authorize(request: HttpRequestBase) = {
    request.addHeader("Authorization", "Bearer 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")
  }

  def getRequest(url: String): String = {
    val client = HttpClientBuilder.create().build()
    val request = new HttpGet(url)
    request.addHeader("Content-Type", "application/json")
    request.addHeader("Accept", "application/json")
    authorize(request)
    val response = client.execute(request)
    val entity = response.getEntity()
    EntityUtils.toString(entity)
  }

}

case class Response(
                   `object`: String,
                   data: Array[Engine]
                 )

case class Engine(
                   id: String,
                   ready: Boolean,
                   owner: String,
                   `object`: String,
                   created: Option[Int],
                   permissions: Option[String],
                   replicas: Option[Int],
                   max_replicas: Option[Int]
                 )

case class TextCompletion(
                           id: String,
                           `object`: String,
                           created: Int,
                           model: String,
                           choices: Array[Choice],
                           error: Option[ApiError]
                         )

case class ApiError(
                     message: String,
                     `type`: String,
                     param: String,
                     code: Option[Double]
                 )

case class Choice(
                   text: String,
                   index: Int,
                   logprobs: Option[Double],
                   finish_reason: String
                 )