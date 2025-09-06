package com.example.moviereccomendationsystem.controller
import com.example.moviereccomendationsystem.service.FunctionService
import org.apache.spark.sql
import org.apache.spark.sql.SparkSession
import org.json.JSONObject
import org.springframework.beans.factory.annotation.{Autowired, Qualifier, Value}
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
                                    val functionService: FunctionService,  // Add this line to inject FunctionService
                                    @Value("${tmdb.api.key}") val tmdbApiKey: String
                                  ){



  @GetMapping(Array("/"))
  def helloScala():String={
    "home"
  }


  @GetMapping(Array("/getSimilarSearches/{searches}"))
  @ResponseBody
  def getSimilarSearches(@PathVariable searches: String): Array[(String,String)] = {

    val sanitizedSearch = searches.replace("'", "''") // Escape single quotes

    val resultDF = sparkSession.sql(s"SELECT * FROM movies_df_temp_view WHERE title LIKE '%$sanitizedSearch%' LIMIT 10")

    val resultList = resultDF.collect().map(row => {

      val movieId:String = row.getAs("movieId")
      val title:String = row.getAs("title")

      (movieId,title)

    })

    resultList
  }

  @GetMapping(Array("/getMoviePage/{movieId}"))
  def getMoviePage(@PathVariable movieId:String,model:Model):String = {

    val tmdbId = sparkSession.sql(s"SELECT tmdbId FROM links_df_temp_view WHERE movieID = ${movieId}")
      .head().getAs[String]("tmdbId")

    val headers: HttpHeaders = new HttpHeaders()

    headers.setAccept(util.Arrays.asList(MediaType.APPLICATION_JSON))

    val httpEntity: HttpEntity[String] = new HttpEntity[String](headers)

    val url = s"https://api.themoviedb.org/3/movie/$tmdbId?api_key=$tmdbApiKey&language=en-US"

    val responseEntity = restTemplate.exchange(url, HttpMethod.GET, httpEntity, classOf[String])

    val responseBody = responseEntity.getBody

    val jsonObject = new JSONObject(responseBody)
    if(jsonObject == null){
      return "notFoundPage"
    }

    val title = jsonObject.getString("title")
    val releaseDate = jsonObject.getString("release_date")
    val posterPath = jsonObject.getString("poster_path")
    val overview = jsonObject.getString("overview")

    model.addAttribute("title",title)
    model.addAttribute("releaseDate",releaseDate)
    model.addAttribute("posterPath",posterPath)
    model.addAttribute("overview",overview)
    model.addAttribute("movieId",movieId)

    "moviePage"
  }


  @PostMapping(Array("/rateMovies"))
  def rateMovies(@RequestParam("rating") rating: String, @RequestParam("movieId") movieId: String): String = {
    ratedMoviesMap(movieId.toLong) = rating.toDouble
    if(rating.toDouble > 3){
      likedMoviesSet += movieId.toLong
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

    model.addAttribute("title","Movies from people with similar ratings")

    model.addAttribute("movies", collaborativeFilteringMovies.asJava)

    "movieReccomendations"
  }

}
