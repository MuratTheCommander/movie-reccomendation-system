package com.example.moviereccomendationsystem.controller

import com.example.moviereccomendationsystem.service.FunctionService
import org.apache.spark.sql
import org.apache.spark.sql.SparkSession
import org.json.JSONObject
import org.springframework.beans.factory.annotation.{Autowired, Qualifier}
import org.springframework.http.{HttpEntity, HttpHeaders, HttpMethod, MediaType}
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation._
import org.springframework.web.client.RestTemplate
import redis.clients.jedis.Jedis

import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters.MutableMapHasAsJava





@Controller
class HomeController @Autowired() (
                                    val sparkSession: SparkSession,
                                    @Qualifier("moviesDFGenerator") val moviesDF: sql.DataFrame,
                                    @Qualifier("linksDFGenerator") val linksDF: sql.DataFrame,
                                    @Qualifier("ratingsDFGenerator") val ratingsDF: sql.DataFrame,
                                    val restTemplate: RestTemplate,
                                    @Qualifier("ratedMoviesSet") val ratedMoviesMap: mutable.HashMap[Long,Double],
                                    @Qualifier("likedMoviesSet") val likedMoviesSet: mutable.HashSet[Long],
                                    @Qualifier("contentBasedMoviesSet") val contentBasedMoviesSet: mutable.LinkedHashMap[Long,String],
                        /*            @Qualifier("jedis0") val jedis0:Jedis,
                                    @Qualifier("jedis1") val jedis1:Jedis, */
                                    @Qualifier("jedis2") val jedis2:Jedis,
                                    @Qualifier("jedis3") val jedis3:Jedis,
                                    val functionService: FunctionService  // Add this line to inject FunctionService


                                  ){



  @GetMapping(Array("/"))
  def helloScala():String={
    println("Hey There")
    "home"
  }


  @GetMapping(Array("/getSimilarSearches/{searches}"))
  @ResponseBody
  def getSimilarSearches(@PathVariable searches: String): Array[(String,String)] = {

    println("Request made to this function getSimilarSearches at: ")

    val sanitizedSearch = searches.replace("'", "''") // Escape single quotes

    val resultDF = sparkSession.sql(s"SELECT * FROM movies_df_temp_view WHERE title LIKE '%$sanitizedSearch%' LIMIT 10")



    println("No problem up to here")

    val resultList = resultDF.collect().map(row => {

      val movieId:String = row.getAs("movieId")
      val title:String = row.getAs("title")

      (movieId,title)

    })

    println(s"Found results for the ${sanitizedSearch} are : ")

    resultList.foreach(println)

    resultList
  }

  @GetMapping(Array("/getMoviePage/{movieId}"))
  def getMoviePage(@PathVariable movieId:String,model:Model):String = {

    println(s"Request to getMoviePage function with the movieId of : ${movieId}")

    val tmdbId = sparkSession.sql(s"SELECT tmdbId FROM links_df_temp_view WHERE movieID = ${movieId}")
      .head().getAs[String]("tmdbId")

    println("Retrieved tmdbId is : " + tmdbId)


    val headers: HttpHeaders = new HttpHeaders()

    headers.setAccept(util.Arrays.asList(MediaType.APPLICATION_JSON))

    val httpEntity: HttpEntity[String] = new HttpEntity[String](headers)

    val url = s"https://api.themoviedb.org/3/movie/$tmdbId?api_key=a2228ad782016126d682e74c8c4d531b&language=en-US"

    val responseEntity = restTemplate.exchange(url, HttpMethod.GET, httpEntity, classOf[String])

    val responseBody = responseEntity.getBody

    val jsonObject = new JSONObject(responseBody)
    if(jsonObject == null){
      return "notFoundPage"
    }

    println("jsonObject is " +  jsonObject)

    println("************")

    val title = jsonObject.getString("title")
    val releaseDate = jsonObject.getString("release_date")
    val posterPath = jsonObject.getString("poster_path")
    val overview = jsonObject.getString("overview")

    println(s"Title: $title, Release Date: $releaseDate, Poster Path: $posterPath ,overview: $overview")

    model.addAttribute("title",title)
    model.addAttribute("releaseDate",releaseDate)
    model.addAttribute("posterPath",posterPath)
    model.addAttribute("overview",overview)
    model.addAttribute("movieId",movieId)

    "moviePage"
  }


  @PostMapping(Array("/rateMovies"))
  def rateMovies(@RequestParam("rating") rating: String, @RequestParam("movieId") movieId: String): String = {
    println("rateMovies function is reached")
    println(s"User has rated ${rating} the movie with the id of ${movieId}: ")


    ratedMoviesMap(movieId.toLong) = rating.toDouble



    println(s"rating of ${rating} given to the movie with the id of ${movieId} has been entered to the ratedMoviesMap")

    println("Content of the ratedMoviesMap is : ")
    for (elem <- ratedMoviesMap) {
      println(s"movie with the id ${elem._1} has been rated ${elem._2}")
    }

    println(s"number of the rated movies is : ${ratedMoviesMap.size}")

    if(rating.toDouble > 3){

      likedMoviesSet += movieId.toLong
      println(s"movie with the id of ${movieId} has been added to the likedMoviesSet with the rating of ${rating}")
      println(s"number of the liked movies is : ${likedMoviesSet.size}")

    }


    "redirect:/"
  }

  @GetMapping(Array("/contentBased"))
  def getContentBased(model: Model): String = {


    if(likedMoviesSet.isEmpty){
    return  "redirect:/"
    }

    val contentBasedMovies :mutable.LinkedHashMap[Long,String]= functionService.contentBasedFiltering(jedis2:Jedis, jedis3: Jedis, likedMoviesSet, 0.2, 0.2,
      0.2,
      contentBasedMoviesSet,sparkSession)

    model.addAttribute("title","Movies similar to your liked movies")
    model.addAttribute("movies", contentBasedMovies.asJava)


    "movieReccomendations"

  }

  import scala.jdk.CollectionConverters._

  @GetMapping(Array("/collaborative"))
  def getCollaborative(model: Model): String = {

    val collaborativeFilteringMovies = functionService.collaborativeFiltering(sparkSession,ratingsDF,ratedMoviesMap)


    println("ratedMoviesMap includes:")
    ratedMoviesMap.foreach{

      case (a,b) => println(s"movie with the id of ${a} is rated ${b}")

    }

    println("Collaborative model includes")

    // Iterate over the original Scala Map (before conversion) for printing
    collaborativeFilteringMovies.foreach {
      case (movieId, movieTitle) =>
        println(s"Movie ID: $movieId, Title: $movieTitle")
    }

    println("Collaborative model ends here")

    model.addAttribute("title","Movies from people with similar ratings")
    model.addAttribute("movies", collaborativeFilteringMovies.asJava)

    "movieReccomendations"
  }



}
