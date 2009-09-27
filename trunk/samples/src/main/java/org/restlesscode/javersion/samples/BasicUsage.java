package org.restlesscode.javersion.samples;

import java.io.IOException;
import java.util.Date;

import org.restlesscode.javersion.MissingObjectException;
import org.restlesscode.javersion.SvnObjectReader;
import org.restlesscode.javersion.SvnObjectWriter;
import org.restlesscode.javersion.SvnRevision;
import org.restlesscode.javersion.SvnStore;
import org.restlesscode.javersion.annotations.SvnContent;
import org.restlesscode.javersion.annotations.SvnProperty;
import org.restlesscode.javersion.annotations.SvnStorable;
import org.tmatesoft.svn.core.SVNException;

public class BasicUsage  {
    
	public static void main( String[] args ) throws IOException, SVNException, MissingObjectException {
		SvnStore svnStore = new SvnStore("file:///Users/mmattozzi/testrepo/");
        
		// SvnStore svnStore = new SvnStore("https://restql.googlecode.com/svn/trunk/", "mike.mattozzi@gmail.com", "Kg3Pm7gb8wq6");
        
		write(svnStore);
		read(svnStore);
    }
	
	private static void write(SvnStore svnStore) throws IOException {
		Movie movie = new Movie();
        movie.setTitle("Where the Wild Things Are");
        movie.setReleaseYear(2009);
        movie.setSynopsis("A kids movie with monsters that may or may not be for kids.");
        movie.setReleaseDay(new Date());
        movie.setRating(3.5f);
        SvnObjectWriter movieSvnWriter = new SvnObjectWriter(svnStore);
        movieSvnWriter.write("movies/kids/whereTheWildThingsAre", movie);
	}
	
	private static void read(SvnStore svnStore) throws IOException, MissingObjectException {
		SvnObjectReader movieSvnReader = new SvnObjectReader(svnStore);
		Movie movie = movieSvnReader.read("movies/new/whereTheWildThingsAre", SvnRevision.HEAD, Movie.class);
		System.out.println(movie);
	}
	
	@SvnStorable(version=1)
	static public class Movie {

		protected String title;
		protected int releaseYear;
		protected String synopsis;
		protected Date releaseDay;
		protected Float rating;
		
		@SvnProperty
		public Float getRating() {
			return rating;
		}

		public void setRating(Float rating) {
			this.rating = rating;
		}

		@SvnProperty
		public Date getReleaseDay() {
			return releaseDay;
		}

		public void setReleaseDay(Date releaseDay) {
			this.releaseDay = releaseDay;
		}

		@SvnProperty
		public String getTitle() {
			return title;
		}
		
		public void setTitle(String title) {
			this.title = title;
		}
		
		@SvnProperty
		public int getReleaseYear() {
			return releaseYear;
		}
		
		public void setReleaseYear(int releaseYear) {
			this.releaseYear = releaseYear;
		}
		
		@SvnContent
		public String getSynopsis() {
			return this.synopsis;
		}
		
		public void setSynopsis(String synopsis) {
			this.synopsis = synopsis;
		}
		
		@Override
		public String toString() {
			return String.format("[%f] %s (%d, %s) - %s", rating, title, releaseYear, releaseDay, synopsis);
		}
	}
}
