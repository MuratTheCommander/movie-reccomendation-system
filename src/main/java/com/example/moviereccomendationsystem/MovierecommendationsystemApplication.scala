package com.example.moviereccomendationsystem

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class MovierecommendationsystemApplication

object MovierecommendationsystemApplication {
	def main(args: Array[String]): Unit = {
		SpringApplication.run(classOf[MovierecommendationsystemApplication], args: _*)
	}
}
