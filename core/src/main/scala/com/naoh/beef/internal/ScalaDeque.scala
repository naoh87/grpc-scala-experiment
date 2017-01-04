package com.naoh.beef.internal

import java.util

import scala.annotation.tailrec

class ScalaDeque[T] private(val inner: util.ArrayDeque[T]) extends AnyVal {
  @inline
  def isEmpty: Boolean = inner.isEmpty

  @inline
  def add(a: T): Boolean = inner.add(a)

  @inline
  def <<(a: T): Boolean = inner.add(a)

  @inline
  def polls[U](f: (T) => U): Unit = pollTraverse(f)

  @inline
  def size: Int = inner.size()

  @inline
  def pollOr[U](f: T => U)(or: => Unit): Unit = {
    poll.map(f).getOrElse(or)
  }

  @inline
  def poll: Option[T] = Option(inner.poll())

  @tailrec
  private def pollTraverse[U](f: T => U): Unit = {
    poll match {
      case Some(x) =>
        f(x)
        pollTraverse(f)
      case _ =>
    }
  }
}

object ScalaDeque {
  def empty[T] = new ScalaDeque[T](new util.ArrayDeque[T])
}