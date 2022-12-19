/*
 * Copyright (c) 2020 by Andrew Charneski.
 *
 * The author licenses this file to you under the
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.simiacryptus.mindseye.art.libs

import com.amazonaws.services.s3.AmazonS3
import com.fasterxml.jackson.databind.{MapperFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.simiacryptus.aws.LinuxUtil.installCurrentAppAsStartupDaemon
import com.simiacryptus.aws.S3Util
import com.simiacryptus.mindseye.art.util._
import com.simiacryptus.notebook.NotebookOutput.AdmonitionStyle
import com.simiacryptus.notebook.{NanoHTTPD, NotebookOutput, StreamNanoHTTPD}
import com.simiacryptus.sparkbook.InteractiveSetup

import java.net.{URI, URLEncoder}
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, Semaphore}

abstract class LibraryNotebook[T <: LibraryNotebook[T]] extends ArtSetup[Object, T] with ArtworkStyleGalleries {

  override def indexStr = "999"
  override def description = <div>Application Launcher</div>.toString.trim
  def applications: Map[String, List[ApplicationInfo[_ <: InteractiveSetup[_, _]]]]

  def install: Boolean = true

  override def postConfigure(log: NotebookOutput) = {
    implicit val l = log
    implicit val s3client: AmazonS3 = S3Util.getS3(log.getArchiveHome)
    if (Option(s3bucket).filter(!_.isEmpty).isDefined)
      log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
    log.onComplete(() => upload(log): Unit)
    import scala.collection.JavaConverters._
    log.eval(()=>{
      System.getProperties.asScala.map(t=>t._1 + " = " + t._2).foreach(println)
    })
    if(install) {
      log.code(()=>{
        installCurrentAppAsStartupDaemon()
      })
    } else {
      log.p("Launcher installation is disabled")
    }

    log.subreport("Launch...", (sub: NotebookOutput) => {
      val handlerPool = Executors.newFixedThreadPool(1)
      val objectMapper = new ObjectMapper()
      objectMapper
        .enable(SerializationFeature.INDENT_OUTPUT)
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        .enable(MapperFeature.USE_STD_BEAN_NAMING)
        .registerModule(DefaultScalaModule)
        .activateDefaultTyping(objectMapper.getPolymorphicTypeValidator())
      for ((familyName, applications) <- applications) {
        sub.h1(familyName)
        sub.p(applications.head.templates(applications.head.templates.head._1).newInstance().description)
        for (application <- applications) {
          sub.h2(application.name)
          val currentlyRunning = new AtomicInteger(0)
          for (template <- application.templates) {
            val templateName = template._1
            val encoded = URLEncoder.encode(application.name, "UTF-8")
            val rawlink = s"${application.name}/$templateName"
            val link = s"$encoded/$templateName"
            val instance = application.get(template._1)
            sub.collapsable(false, AdmonitionStyle.Info, templateName,
              s"""
                 |[Launch](/$link)
                 |
                 |<pre>${objectMapper.writeValueAsString(instance)}</pre>
                 |""".stripMargin.trim)
            sub.getHttpd.addGET(
              rawlink,
              StreamNanoHTTPD.asyncHandler(
                handlerPool,
                NanoHTTPD.MIME_HTML,
                out => {
                  val runID = UUID.randomUUID()
                  val name = s"/$encoded/$templateName/${runID.toString}"
                  val onStart = new Semaphore(0)
                  val thread = new Thread(() => {
                    try {
                      if (0 == currentlyRunning.getAndIncrement()) {
                        log.subreport(name, (job: NotebookOutput) => {
                          out.write(<html>
                            <head></head>
                            <body>
                              <a href={s"/${job.getFileName}.html"}>Open New Task...</a>
                            </body>
                          </html>.toString.getBytes("UTF-8"))
                          onStart.release()
                          try {
                            instance.apply(job)
                          } finally {
                            job.close()
                          }
                        })
                      } else {
                        out.write(<html>
                          <head></head>
                          <body>
                            <h2>ERROR: Task Already Running</h2>
                          </body>
                        </html>.toString.getBytes("UTF-8"))
                        onStart.release()
                      }
                    } finally {
                      currentlyRunning.decrementAndGet()
                    }
                  })
                  thread.setName(name)
                  thread.start()
                  onStart.acquire()
                },
                false
              )
            )
          }
        }
      }
    })

    while (true) {
      Thread.sleep(60 * 1000)
    }

    null
  }
}
