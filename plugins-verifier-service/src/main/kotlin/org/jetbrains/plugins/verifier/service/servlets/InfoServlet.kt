package org.jetbrains.plugins.verifier.service.servlets

import com.jetbrains.pluginverifier.misc.bytesToGigabytes
import com.jetbrains.pluginverifier.misc.bytesToMegabytes
import com.jetbrains.pluginverifier.output.HtmlBuilder
import org.jetbrains.plugins.verifier.service.ide.IdeFilesManager
import org.jetbrains.plugins.verifier.service.service.BaseService
import org.jetbrains.plugins.verifier.service.service.ServerInstance
import org.jetbrains.plugins.verifier.service.setting.Settings
import org.jetbrains.plugins.verifier.service.status.ServerStatus
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class InfoServlet : BaseServlet() {

  private val serverStatus = ServerStatus(getTaskManager())

  companion object {
    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd hh:mm:ss")
  }

  override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
    val path = getPath(req, resp) ?: return
    if (path.endsWith("control-service")) {
      val adminPassword = req.getParameter("admin-password")
      if (adminPassword == null || adminPassword != Settings.SERVICE_ADMIN_PASSWORD.get()) {
        resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Incorrect password")
        return
      }
      val serviceName = req.getParameter("service-name")
      if (serviceName == null) {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Service name is not specified")
        return
      }
      val command = req.getParameter("command")
      when (command) {
        "start" -> changeServiceState(serviceName, resp) { it.start() }
        "resume" -> changeServiceState(serviceName, resp) { it.resume() }
        "pause" -> changeServiceState(serviceName, resp) { it.pause() }
        "stop" -> changeServiceState(serviceName, resp) { it.stop() }
        else -> resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown command")
      }
    } else {
      processStatus(resp)
    }
  }

  private fun changeServiceState(serviceName: String, resp: HttpServletResponse, action: (BaseService) -> Boolean) {
    val service = ServerInstance.services.find { it.serviceName == serviceName }
    if (service == null) {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Service $serviceName is not found")
    } else {
      if (action(service)) {
        sendOk(resp, "Service's $serviceName state is changed to ${service.getState()}")
      } else {
        resp.sendError(HttpServletResponse.SC_CONFLICT, "Service $serviceName can't be paused")
      }
    }
  }

  private fun processStatus(resp: HttpServletResponse) {
    sendBytes(resp, generateStatusPage(), "text/html")
  }

  private fun generateStatusPage(): ByteArray {
    val byteOS = ByteArrayOutputStream()
    val printWriter = PrintWriter(byteOS)
    HtmlBuilder(printWriter).apply {
      html {
        head {
          title("Server status")
          style {
            +"""table, th, td {
              border: 1px solid black;
              border-collapse: collapse;
            }"""
          }
        }
        body {
          div {
            h1 {
              +("Plugin Verifier Service " + getAppVersion())
            }
            h2 {
              +"Runtime parameters:"
            }
            ul {
              Settings.values().forEach { s ->
                li {
                  +(s.key + " = " + if (s.encrypted) "*****" else s.get())
                }
              }
            }

            h2 {
              +"Status:"
            }
            ul {
              val (totalMemory, freeMemory, usedMemory, maxMemory) = serverStatus.getMemoryInfo()
              li { +"Total memory: ${totalMemory.bytesToMegabytes()} Mb" }
              li { +"Free memory: ${freeMemory.bytesToMegabytes()} Mb" }
              li { +"Used memory: ${usedMemory.bytesToMegabytes()} Mb" }
              li { +"Max memory: ${maxMemory.bytesToMegabytes()} Mb" }

              val (totalUsage) = serverStatus.getDiskUsage()
              li { +"Total disk usage: ${totalUsage.bytesToGigabytes()} Gb" }
            }

            h2 {
              +"Services:"
            }
            ul {
              ServerInstance.services.forEach { service ->
                val serviceName = service.serviceName
                li {
                  +(serviceName + " - ${service.getState()}")
                  form("control-$serviceName", "display: inline;", "/info/control-service") {
                    input("submit", "command", "start")
                    input("submit", "command", "resume")
                    input("submit", "command", "pause")
                    input("submit", "command", "stop")
                    input("hidden", "service-name", serviceName)
                    +"Admin password: "
                    input("password", "admin-password")
                  }
                }
              }
            }
            h2 {
              +"Available IDEs: "
            }
            ul {
              IdeFilesManager.ideList().forEach {
                li {
                  +it.toString()
                }
              }
            }

            h2 {
              +"Running tasks"
            }
            table("width: 100%") {
              tr {
                th { +"ID" }
                th { +"Task name" }
                th { +"Start time" }
                th { +"State" }
                th { +"Message" }
                th { +"Completion %" }
                th { +"Total time (ms)" }
              }

              serverStatus.getRunningTasks().forEach { (taskId, taskName, startedDate, state, progress, totalTimeMs, message) ->
                tr {
                  td { +taskId.toString() }
                  td { +taskName }
                  td { +DATE_FORMAT.format(startedDate) }
                  td { +state.toString() }
                  td { +message }
                  td { +(progress * 100.0).toString() }
                  td { +(totalTimeMs.toString()) }
                }
              }
            }
          }
        }
      }
    }
    printWriter.close()
    return byteOS.toByteArray()
  }

}
