package com.e8kor.cvk.crawler.model

import java.time.LocalDateTime
import java.util.Date

case class Candidate(name: String,
                     uri: String,
                     association: String,
                     number: Int,
                     photo: Option[String],
                     registration: String,
                     details: String,
                     attachments: Map[String, String],
                     references: Map[String, String],
                     hash: Option[Int],
                     created: Date)
