package entertheblack.game;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import entertheblack.fight.Ship;
import entertheblack.gui.Screen;
import entertheblack.menu.Assets;
import entertheblack.menu.MainMenu;
import entertheblack.storage.Variant;

// Interface of a solar system.
// The star is also stored as Planet object.

public class Star extends Screen {
	boolean move = false;
	boolean left = false;
	boolean right = false;
	
	ArrayList<Fleet> fleets;

	boolean inRange = false;
	
	private static final int size = 4096;
	double zoom = 540.0/size;
	public Planet[] planets;
	Planet zoomLock; // The planet that is in the center of zoom. Used to smoothly close into planets.
	double lockStrength = 0; // Factor of how planet-centered and how star-centered the current camera position is.
	public int x, y; // Position on the map.
	Ship ship;
	String name;
	
	@Override
	public void keyPressed(KeyEvent e) {
		if (e.getKeyCode() == Assets.Controls[1]) {
			right = true;
		}
		if (e.getKeyCode() == Assets.Controls[0]) {
			left = true;
		}
		if (e.getKeyCode() == Assets.Controls[4]) {
			move = true;
		}
		if(e.getKeyChar() == 'm') {
			Assets.screen = Assets.curWorld.map;
			Assets.curWorld.map.activate(this);
		}
		if(e.getKeyChar() == 'p') {
			Assets.saveGame(Assets.curWorld, this);
		}
		if(e.getKeyCode() == 27) {
			Assets.screen = new MainMenu();
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
		if (e.getKeyCode() == Assets.Controls[1]) {
			right = false;
		}
		if (e.getKeyCode() == Assets.Controls[0]) {
			left = false;
		}
		if (e.getKeyCode() == Assets.Controls[4]) {
			move = false;
		}
	}
	
	public void activate(Variant mainShip) {
		ship = new Ship(mainShip, 0, size - (size >> 2));
		fleets = new ArrayList<>();
		zoomLock = planets[0];
		
		// TODO: Properly initialize fleets!
		//if(getPlanet("Earth") != null)
			//fleets.add(new Fleet("Human", getPlanet("Earth").x, getPlanet("Earth").y, Assets.shipData.get(0)));
	}
	
	public Planet getPlanet(String name) {
		for(int i = 0; i < planets.length; ++i) {
			if(planets[i].name.equals(name))
				return planets[i];
		}
		return null;
	}
	
	public Star(String name, String str, String file) { // Data must not contain ' '!
		this.name = name;
		// Get the coordinates in the map:
		String[] lines = str.split("\n");
		for(int i = 0; i < lines.length; i++) {
			if(lines[i].contains("{")) // Avoid reading the data of a sub and printing errors.
				break;
			String[] val = lines[i].split("=");
			if(val.length < 2)
				continue;
			if(val[0].equals("X")) { // In milli parsec.
				x = Integer.parseInt(val[1]);
			} else if(val[0].equals("Y")) {  // In milli parsec.
				y = Integer.parseInt(val[1]);
			} else {
				System.err.println("Error in "+file+" in planet definition of "+name+ " in line "+(i+1)+":");
				System.err.println("Unknown argument for type Star \"" + val[0] + "\" with value \"" + val[1] + "\". Skipping line!");
			}
		}
		
		System.out.println("Loading star system "+name+" at ("+x+", "+y+").");
		char [] data = str.toCharArray();
		int depth = 0;
		List<Planet> lPlanets = new ArrayList<>();
		StringBuilder stb = new StringBuilder();
		for(int i = 0; i < data.length; i++) {
			switch(depth) {
			case 0:
				if(data[i] == '{') {
					name = stb.toString();
					stb = new StringBuilder();
					depth = 1;
				} else if(data[i] != '\n') {
					stb.append(data[i]);
				} else {
					stb = new StringBuilder(); // Name will only be the line directly in front of '{'
				}
				break;
			default:
				if(data[i] == '}') {
					depth--;
					if(depth == 0)
						lPlanets.add(new Planet(name, stb.toString(), lPlanets.size() == 0 ? null : lPlanets.get(0), file));
					else
						stb.append(data[i]);
				} else {
					stb.append(data[i]);
					if(data[i] == '{')
						depth++;
				}
				break;
			}
		}
		if(depth != 0) {
			System.err.println("Error in "+file+":");
			System.err.println("Could not find \"}\"!");
		}
		
		planets = lPlanets.toArray(new Planet[0]);
		
		activate(Assets.variants.get(0));
	}
	
	public void paint(Graphics2D g) {
		g.translate(960, 540);
		g.scale(zoom, zoom);
		g.translate(-(int)(lockStrength*zoomLock.x), -(int)(lockStrength*zoomLock.y));
		g.drawImage(Assets.bg, -size, -size, size*2, size*2, null);
		g.drawImage(Assets.bg, -size*3, -size, size*2, size*2, null);
		g.drawImage(Assets.bg, size, -size, size*2, size*2, null);
		for(int i = 0; i < planets.length; i++) {
			planets[i].paint(g);
		}
		for(Fleet f : fleets) {
			f.paint(g);
		}
		ship.paint(g);
		g.translate((int)(lockStrength*zoomLock.x), (int)(lockStrength*zoomLock.y));
		g.scale(-zoom, -zoom);
	}
	
	int d = 0;
	@Override
	public void update() {
		ship.fly(move, right, left);
		d++;
		// Determine the zoomLock which is the closest planet to the ship.
		double r = 1.0e300;
		for(int i = 0; i < planets.length; i++) {
			Planet p = planets[i];
			double deltax = ship.x+ship.r-p.x;
			double deltay = ship.y+ship.r-p.y;
			double r2 = Math.sqrt(deltax*deltax+deltay*deltay);
			if(r2 < r) {
				r = r2;
				zoomLock = p;
			}
			p.updateOrbit(d);
		}
		// Create the lockstrength which should smoothly transition from 0 to 1 as the ship gets closer.
		if(r > zoomLock.r*8) {
			inRange = false;
			lockStrength = 0;
		} else if (r < zoomLock.r*2) {
			if(r < zoomLock.r && !inRange) {
				Assets.screen = new LandingScreen(zoomLock, this); // Automatically land on the planet.
				inRange = true;
			}
			lockStrength = 1;
		} else {
			inRange = false;
			lockStrength = 1-(r - zoomLock.r*2)/6/zoomLock.r;
		}
		for(Fleet f : fleets) {
			f.update(ship);
		}
		
		r += zoomLock.r*4;
		double r2 = Math.sqrt(ship.x*ship.x + ship.y*ship.y) + planets[0].r*4;
		zoom = lockStrength*0.125*size/r + (1 - lockStrength)*0.125*size/r2;
		lockStrength *= 2; // Lock the position faster to ensure the ship is always visible.
		if(lockStrength > 1)
			lockStrength = 1;
		if(ship.y-ship.r < -size || ship.x-ship.r < -size || ship.x+ship.r > size || ship.y+ship.r > size) {
			Assets.screen = new HyperSpace(Assets.curWorld.map, ship.v, this);
		}
	}
	
	public void save(StringBuilder sb) {
		// TODO: Save what resources have been collected.
	}
}
