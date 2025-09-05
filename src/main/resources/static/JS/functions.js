document.addEventListener("DOMContentLoaded", function() {
    // Debounce function to limit how often the search is executed
    function debounce(func, delay) {
        let timeout;
        return function (...args) {
            const context = this;
            clearTimeout(timeout);
            timeout = setTimeout(() => func.apply(context, args), delay);
        };
    }

    // Modified listMovies function
    function listMovies(value) {
        var similarSearches = document.getElementById("similar-searches");

        if (similarSearches) {
            similarSearches.innerHTML = ''; // Clear any previous search results
        } else {
            console.log("Element not found: similar-searches");
            return;
        }

        if (typeof(value) === 'undefined') return;

        var xhttp = new XMLHttpRequest();

        xhttp.onreadystatechange = function() {
            if (this.readyState === 4 && this.status === 200) {
                var movieJson = JSON.parse(xhttp.responseText);

                console.log("Retrieved json is  : " + typeof(movieJson))

                 movieJson.forEach(function(movie, index) {
                            console.log(`Movie ${index}:`, movie);
                        });

                for (const movie of movieJson) {
                    const liOfSimilarSearches = document.createElement("li");
                    liOfSimilarSearches.className = "similar-searches-li";
                    liOfSimilarSearches.textContent = movie._2;
                    liOfSimilarSearches.setAttribute("data-movieId", movie._1); // Store movie ID
                    similarSearches.appendChild(liOfSimilarSearches);
                }
            }
        };

        xhttp.open('GET', '/getSimilarSearches/' + encodeURIComponent(value), true);
        xhttp.send();
    }

    // Trigger listMovies with debounce on input change
    document.getElementById("search-input").addEventListener("input", debounce(function () {
        listMovies(this.value);
    }, 300));

    // Event listener for dynamically created <li> elements
    document.getElementById("similar-searches").addEventListener('click', function(event) {
        if (event.target.classList.contains('similar-searches-li')) {
            const movieId = event.target.getAttribute("data-movieId");
            const tmdbId = event.target.getAttribute("data-tmdbId")
            console.log("Clicked movieId:", movieId);
            console.log("Clicked tmdbId:", tmdbId);
            window.location.href = "/getMoviePage/" + encodeURIComponent(movieId);
        //    requestMoviePage(movieId); // Call requestMoviePage with movieId
        }
    });

    // Function to request a movie page with a specific movieId
 /*   function requestMoviePage(movieId) {
        console.log("Hey from requestMoviePage for movieId:", movieId);

        var xhttp = new XMLHttpRequest();

        // Replace {movieId} placeholder with the actual movieId value
        xhttp.open('GET', '/getMoviePage/' + encodeURIComponent(movieId), true);

        // Set up a callback to handle the response
        xhttp.onload = function () {
            if (xhttp.status === 200) {
          //      console.log("Movie page loaded successfully:", xhttp.responseText);
                // Additional logic to update the UI with the response data
            } else {
                console.error("Failed to load movie page:", xhttp.status, xhttp.statusText);
            }
        };

        xhttp.onerror = function () {
            console.error("There was an error making the request.");
        };

        // Send the request
        xhttp.send();
    }  */
});
