package com.e8kor.cvk.crawler.model

import java.util.Date

case class Candidate(uri: String,
                     name: String,
                     gender: Boolean,
                     birthday: Date,
                     canApply: Boolean,
                     electionRegion: Option[Int],
                     partyAssociation: Option[String],
                     party: String,
                     partyNum: Option[Int],
                     partyListNum: Option[Int],
                     education: String,
                     employment: String,
                     employer: Option[String],
                     city: String,
                     subject: Option[String],
                     photo: Option[String],
                     notParsedDetails: List[String],
                     registrationNum: Int,
                     registrationDate: Date,
                     attachment: Map[String, String],
                     reference: Map[String, String],
                     notParsed: Map[String, String]) {
  var hash: Int = -1
}

object CandidateAux {

  def `new`(uri: String, name: String, hash: Int): Candidate = {
    val item = new Candidate(
      uri = uri,
      name = name,
      gender = false,
      birthday = new Date(),
      canApply = false,
      electionRegion = None,
      partyAssociation = None,
      party = "-",
      partyNum = None,
      partyListNum = None,
      education = "-",
      employment = "-",
      employer = None,
      city = "-",
      subject = None,
      photo = None,
      notParsedDetails = List.empty,
      registrationNum = -1,
      registrationDate = new Date(),
      attachment = Map.empty[String, String],
      reference = Map.empty[String, String],
      notParsed = Map.empty[String, String]
    )
    item.hash = hash
    item
  }

//  def unapply(candidate: Candidate) = {
//    val Some(
//      (
//        uri,
//        name,
//        gender,
//        birthday,
//        canApply,
//        electionRegion,
//        partyAssociation,
//        party,
//        partyNum,
//        partyListNum,
//        education,
//        employment,
//        employer,
//        city,
//        subject,
//        photo,
//        notParsedDetails,
//        registrationNum,
//        registrationDate,
//        attachment,
//        reference,
//        notParsed
//      )
//    ) = Candidate.unapply(candidate)
//    val t = (
//      uri,
//      name,
//      gender,
//      birthday,
//      canApply,
//      electionRegion,
//      partyAssociation,
//      party,
//      partyNum,
//      partyListNum,
//      education,
//      employment,
//      employer,
//      city,
//      subject,
//      photo,
//      notParsedDetails,
//      registrationNum,
//      registrationDate,
//      attachment,
//      reference,
//      notParsed,
//      candidate.hash
//    )
//    return t
//  }

}
