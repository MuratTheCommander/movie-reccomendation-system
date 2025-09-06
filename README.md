# MovieLens Recommendation System (Work in Progress)

This project demonstrates a **recommendation system** built on top of the [MovieLens dataset](https://grouplens.org/datasets/movielens/).  
It combines **Spring Boot**, **Scala**, and **Apache Spark** to process user–movie interactions and leverages **Redis** for caching to achieve fast, scalable recommendation pipelines.

⚠️ **Note:** This project is a **work in progress**. It is currently intended for learning and demonstration purposes, and is **not yet optimized for production use**.

---

## Tech Stack

- **Spring Boot** – REST API and service layer  
- **Scala** – data transformation and ML pipeline logic  
- **Apache Spark** – distributed data processing and recommendation computations  
- **Redis** – caching layer for fast access to recommendation results  

---

## Requirements

- Java 17+  
- Scala 2.12+  
- Apache Spark (local or cluster mode)  
- Redis server (must be installed and running before starting the app)  
- Maven or Gradle (for building the Spring Boot service)

### Starting Redis
On Ubuntu / WSL:
```bash
sudo service redis-server start
redis-cli ping   # should reply PONG
```

Using Docker:
```bash
docker run --name redis -d -p 6379:6379 redis
```

---

## Dataset: MovieLens (ml-latest-small)

This project uses the [MovieLens dataset](https://grouplens.org/datasets/movielens/) (ml-latest-small), a widely used benchmark for recommender system research.

- **Size**: 100,836 ratings, 3,683 tags, across 9,742 movies  
- **Users**: 610 anonymous users, each with at least 20 ratings  
- **Files**:
  - `ratings.csv` – user ratings (1–5 stars, half-star increments)  
  - `movies.csv` – movie titles and genres  
  - `tags.csv` – user-generated tags  
  - `links.csv` – IDs for IMDB and TMDB  

---

## Movie Metadata & Posters

- **Movie metadata** comes from the [MovieLens dataset](https://grouplens.org/datasets/movielens/) by GroupLens Research, University of Minnesota.
- **Movie posters and images** are retrieved using TMDB IDs via [The Movie Database (TMDB)](https://www.themoviedb.org/).

**Attribution & Disclaimer:**
- MovieLens data © GroupLens Research, University of Minnesota.  
- Posters and images © TMDB.  
- **This product uses the TMDB API but is not endorsed or certified by TMDB.**

> In the application UI where posters are displayed, include the official “Powered by TMDB” logo and the disclaimer above (e.g., a footer near the posters). Use one of TMDB’s approved logos and link to https://www.themoviedb.org.

---

## TMDB Attribution & Terms (Summary)

- **Logo & Notice (required):** Display an approved **TMDB logo** and the notice:  
  _“This product uses the TMDB API but is not endorsed or certified by TMDB.”_  
  Place it visibly in your app (e.g., footer/credits/About page near where posters appear).  
- **Link back:** Link the attribution to **https://www.themoviedb.org**.  
- **Branding rules:** Use only approved logos; do not alter color/aspect ratio; TMDB’s branding must be **less prominent** than your app’s branding.  
- **Usage:** Free for **non-commercial** projects with attribution. Commercial use requires a TMDB commercial license.  
- **API keys:** Keep your TMDB API key private (do not commit it to version control).

---

## Dataset License & Citation (MovieLens)

This project includes data derived from the [MovieLens dataset](https://grouplens.org/datasets/movielens/), released by the GroupLens Research Group at the University of Minnesota.  

The dataset is provided **for research and educational purposes only** and may not be used for commercial purposes without prior permission from GroupLens.  

For full license and citation details, see the official MovieLens README:  
👉 [https://files.grouplens.org/datasets/movielens/ml-latest-small-README.html](https://files.grouplens.org/datasets/movielens/ml-latest-small-README.html)

**Citation:**  
F. Maxwell Harper and Joseph A. Konstan. 2015.  
*The MovieLens Datasets: History and Context.*  
ACM Transactions on Interactive Intelligent Systems (TiiS) 5, 4: 19:1–19:19.  
[https://doi.org/10.1145/2827872](https://doi.org/10.1145/2827872)

---

## Status & Roadmap

This is an **active, evolving project**. Planned improvements include:
- Performance optimizations in the Spark data pipeline  
- Exposing recommendation results through REST API endpoints in Spring Boot  
- Scaling tests with larger MovieLens datasets  
- Adding visualizations of recommendation quality  

## Environment Setup

This project requires a TMDB API Key (v3 auth, 32 characters) for retrieving posters and metadata.

⚠️ Do not commit your TMDB API key to version control. Keep it private.

Setting the API Key
IntelliJ IDEA

- Open Run → Edit Configurations.

- Select your Spring Boot run configuration.

- Add an environment variable: TMDB_API_KEY=your_tmdb_v3_key_here

---
*Author: MuratTheCommander* • *License: MIT*
