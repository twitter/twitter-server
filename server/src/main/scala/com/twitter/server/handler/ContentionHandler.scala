package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.jvm.ContentionSnapshot
import com.twitter.server.util.HttpUtils.newOk
import com.twitter.util.Future
import com.twitter.util.logging.Logging
import java.lang.management.ManagementPermission

class ContentionHandler extends Service[Request, Response] with Logging {

  // Must check the installed java.lang.SecurityManager for java.lang.management.ManagementPermission("control")
  // access. If no java.lang.SecurityManager, we assume the instantiation of the ContentionSnapshot will succeed.
  private[this] val contentionSnapshot: Option[ContentionSnapshot] = {
    val permission = new ManagementPermission("control")
    Option(System.getSecurityManager) match {
      case Some(securityManager) =>
        try {
          securityManager.checkPermission(permission)
          Some(new ContentionSnapshot)
        } catch {
          case e: SecurityException => // not allowed
            warn("Contention snapshotting is not allowed by SecurityManager.", e)
            None
        }
      case _ => // no security manager
        Some(new ContentionSnapshot)
    }
  }

  def apply(req: Request): Future[Response] = {
    contentionSnapshot match {
      case Some(snapshot) =>
        val snap = snapshot.snap()
        val deadlockMsg =
          if (snap.deadlocks.isEmpty) ""
          else {
            "DEADLOCKS:\n\n%s\n\n".format(snap.deadlocks.mkString("\n\n"))
          }

        val msg = "%sBlocked:\n%s\n\nLock Owners:\n%s".format(
          deadlockMsg,
          snap.blockedThreads.mkString("\n"),
          snap.lockOwners.mkString("\n")
        )

        newOk(msg)
      case _ =>
        val msg =
          "Contention snapshotting is not enabled due to SecurityManager restrictions.\n" +
            "Please ensure that the java.lang.management.ManagementPermission(\"control\") is allowed."
        newOk(msg)
    }
  }
}
