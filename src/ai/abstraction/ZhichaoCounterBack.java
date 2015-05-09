/**
* @Title Project 4
* @student Zhichao Cao
* @email zc77@drexel.edu
* 
*/

package ai.abstraction;

import java.nio.IntBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import rts.GameState;
import rts.PhysicalGameState;
import rts.Player;
import rts.PlayerAction;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;
import ai.AI;
import ai.abstraction.pathfinding.PathFinding;


public class ZhichaoCounterBack extends AbstractionLayerAI {
    
    // Strategy implemented by this class:
	// If the map is small, we only need 1 worker and 1 barrack, else we build 2 workers and 2 barracks
	// If the map has size less than 12x12, we send units to attack if we have 2 units or more
    // If the map has size less than 16x16, we send units to attack if we have 3 units or more
    // Otherwise, we send units to attack if we have 4 units or more
    // If we do not have a base, try to build one
    // If we have a base, train worker until we have 2 workers
	// For small map:
	// We build one barrack, train heavy units until we have two heavy ones, then we train light
	// The safe distance is 5
	// For mid map:
	// We build two barracks, train heavy units until we have three heavy ones, then we train light
	// The safe distance is 7
	// For big map:
	// We build two barracks
    // If we have a barrack: train heavy units
    // If we have an other barrack: train light units
	// The safe distance is 10
    // If we have a group of units whose total number is over the predefined rule: send them to attack to the nearest enemy unit
    // If we have a free worker: do this if needed: build base, build barracks, harvest resources
	// If enemy units are closing our base which means they are attempting to attack the base, send ranged to destroy them
	// If enemy units are near any our units, send the unit to destroy them
	// If the number of our army units is more than enemy's army units and is more than half of enemy's worker units, send our units to destroy them
	// If there exists enemy closed to our units, send our units to destroy it
	
	Random r = new Random();
    UnitTypeTable utt;
    UnitType workerType;
    UnitType baseType;
    UnitType barracksType;
    UnitType lightType;
    UnitType rangedType;
    UnitType heavyType;
    boolean smallMap = false;
    boolean midMap = false;
    boolean bigMap = false;
    boolean upPos = true;
    int bx = 0;
    int by = 0;
    int init = 0;
    int safeDist = 0;
    int closedDist = 0;
	
	public ZhichaoCounterBack(UnitTypeTable a_utt, PathFinding a_pf) {
		super(a_pf);
        utt = a_utt;
        workerType = utt.getUnitType("Worker");
        baseType = utt.getUnitType("Base");
        barracksType = utt.getUnitType("Barracks");
        lightType = utt.getUnitType("Light");
        heavyType = utt.getUnitType("Heavy");
        rangedType = utt.getUnitType("Ranged");
	}

	@Override
	public void reset() {
		
	}

	@Override
	public AI clone() {
		return new ZhichaoCounterBack(utt, pf);
	}

	@Override
	public PlayerAction getAction(int player, GameState gs) throws Exception {
		PhysicalGameState pgs = gs.getPhysicalGameState();
        Player p = gs.getPlayer(player);
        PlayerAction pa = new PlayerAction();        
        int nbase = 0;
        int nbarrack = 0;
        int nheavy = 0;
        int nlight = 0;
        int nranged = 0;
        int nworker = 0;
        int rndBarrack = 0;
        int width = 0;
        int height = 0;
        int requiredWorker = 0;
        int requiredBarrack = 0;
        int attackUnits = 0;
        boolean denf = true;
        Unit fstBarrack = null;
        Unit sndBarrack = null;
        Unit uBase = null;
        List<Unit> armyList = new LinkedList<Unit>();
        List<Unit> enemyArmyList = new LinkedList<Unit>();
        List<Unit> enemyWorkerList = new LinkedList<Unit>();
        List<Unit> aroundEnemyList = new LinkedList<Unit>();
        List<Unit> workers = new LinkedList<Unit>();
        List<Unit> freeWorkers = new LinkedList<Unit>();
        String mapStr = "";
        
        // Receive information from the game world
        width = pgs.getWidth();
        height = pgs.getHeight();
        for (Unit u2 : pgs.getUnits()) {
    		if (u2.getType() == baseType
                    && u2.getPlayer() == p.getID()) {
    			uBase = u2;
            }
    	}
        if(uBase != null && init == 0) {
        	bx = uBase.getX();
        	by = uBase.getY();
	        if(uBase.getX() < width / 2 && uBase.getY() < height / 2) {
	        	upPos = true;
	        }
	        else {
	        	upPos = false;
	        }
	        init++;
        }                
        
        // Calculate the number of different Units including workers
        nheavy = calHeavyUnit(p, pgs);
        nlight = calLightUnit(p, pgs);
        nranged = calRangedUnit(p, pgs);
        armyList = calAllUnits(gs, p, pgs);
        enemyArmyList = calEnemyArmyUnits(gs, p, pgs);
        enemyWorkerList = calEnemyWorkerUnits(gs, p, pgs);
        aroundEnemyList = calAroundEnemy(armyList, p, pgs);
        nworker = calWorkerUnit(p, pgs);
        for (Unit u : pgs.getUnits()) {
            if (u.getType() == workerType && u.getPlayer() == player) {
                workers.add(u);
            }
        }
        freeWorkers.addAll(workers);
        
        // Calculate the number of different building
        nbase = calBase(p, pgs);
        nbarrack = calBarrack(p, pgs);
        
        // Rule: If the map has size less than 12x12, we only need 1 worker and 1 barrack, else we build 2 workers and 2 barracks
        // If the map has size less than 12x12, we send units to attack if we have 2 units or more
        // If the map has size less than 16x16, we send units to attack if we have 3 units or more
        // Otherwise, we send units to attack if we have 4 units or more
        if(width < 12 || height < 12) {
        	requiredWorker = 1;
        	requiredBarrack = 1;
        	attackUnits = 2;
        	smallMap = true;
        	safeDist = 6;
        	mapStr = "s";
        	closedDist = 4;
        }
        else if(width < 16 || height < 16) {
        	requiredWorker = 2;
        	requiredBarrack = 2;
        	attackUnits = 3;
        	midMap = true;
        	safeDist = 8;
        	mapStr = "m";
        	closedDist = 4;
        }
        else {
        	requiredWorker = 2;
        	requiredBarrack = 2;
        	attackUnits = 4;
        	bigMap = true;
        	safeDist = 11;
        	mapStr = "b";
        	closedDist = 4;
        }
        
        // Rule: Keep training workers until we have two
        if(nworker < requiredWorker) {
            for (Unit u : pgs.getUnits()) {
                if (u.getType() == baseType
                        && u.getPlayer() == player
                        && gs.getActionAssignment(u) == null) {
                    buildWorker(u, p, pgs);
                }
            }
        }
        
        // Rule: We must have at least one base
        if(nbase < 1) {
        	freeWorkers = buildBase(freeWorkers, p, pgs);
        }
        
        // Rule: Build barrack until we have two
        if(nbarrack < requiredBarrack) {
        	freeWorkers = buildBarrack(freeWorkers, p, pgs);
        }
        
        // Rule: If we have one barrack and map is small, train heavy units until we have two, then train light
        // If we have one barrack and map is small, train heavy units until we have three, then train light
        if(nbarrack == 1) {
        	for (Unit u : pgs.getUnits()) {
                if (u.getType() == barracksType && u.getPlayer() == player && gs.getActionAssignment(u) == null) {
                	if((smallMap == true && armyList.size() < 2) || (midMap == true && armyList.size() < 3) || (bigMap == true && armyList.size() < 4)) {
                		trainHeavy(u, p, pgs);
                	}
                	else {
                		trainLight(u, p, pgs);
                	}
                }
            }
        }
        
        // Rule: If we have two barracks and map is mid, and the number of units are less than 3, train heavy units
        if((nbarrack == 2 && midMap == true)) {
        	for (Unit u : pgs.getUnits()) {
	            if (u.getType() == barracksType
	                    && u.getPlayer() == player
	                    && gs.getActionAssignment(u) == null) {
	            	if(armyList.size() < 3) {
	            		trainHeavy(u, p, pgs);
	            	}
	            	else {
	            		trainLight(u, p, pgs);
	            	}
	            }
	        }
        }
        
        // Rule: If we have two barracks and map is big, and the number of units are less than 4, train heavy units
       if((nbarrack == 2 && bigMap == true)) {
	        for (Unit u : pgs.getUnits()) {
	            if (u.getType() == barracksType
	                    && u.getPlayer() == player
	                    && gs.getActionAssignment(u) == null) {
	            	if(armyList.size() < 4) {
	            		trainHeavy(u, p, pgs);
	            	}
	            	else {
	            		trainLight(u, p, pgs);
	            	}
	            }
	        }
        }       

       // Rule: If there are any enemy units closed to out units, send our units to destroy them
       if(aroundEnemyList.size() > 0) {
    	   attackAround(armyList, p, pgs);
       }
        
        // Rule: Calculate the group of units, if we have 2 ranged and 1 light, send them to attack
        if(armyList.size() >= attackUnits || (armyList.size() > enemyArmyList.size() && armyList.size() > enemyWorkerList.size()/2)) {
        	sendToAttack(armyList, p, pgs);
        }
        
        // Rule: If no other tasks for workers, send them to gather resources
        if(freeWorkers.size() > 0) {
        	sendToResource(freeWorkers, p, pgs);
        }
        
        // Rule: If enemy units trying to attack the base, send ranged unit to destroy them
        if(denf == true) {
        	sendToDefense(armyList, p, pgs);
        }
        
		return translateActions(player, gs);
	}
	

    public void buildWorker(Unit u, Player p, PhysicalGameState pgs) {
        if (p.getResources() > workerType.cost) {
            train(u, workerType);
        }
    }
    
    public List<Unit> buildBase(List<Unit> workers, Player p, PhysicalGameState pgs) {
    	List<Unit> freeWorkers = new LinkedList<Unit>();
    	List<Integer> reservedPositions = new LinkedList<Integer>();
        freeWorkers.addAll(workers);
        if(workers.isEmpty()) {
            return freeWorkers;
        }
    	if(!freeWorkers.isEmpty()) {
    		if (p.getResources() > baseType.cost) {
                Unit u = freeWorkers.remove(0);
                int pos = findBuildingPosition(reservedPositions, u, p, pgs);
                build(u, baseType, pos % pgs.getWidth(), pos / pgs.getWidth());
                reservedPositions.add(pos);
            }
    	}
    	return freeWorkers;
    }
    
    public List<Unit> buildBarrack(List<Unit> workers, Player p, PhysicalGameState pgs) {
    	List<Unit> freeWorkers = new LinkedList<Unit>();
    	List<Integer> reservedPositions = new LinkedList<Integer>();
    	freeWorkers.addAll(workers);
    	if(workers.isEmpty()) {
            return freeWorkers;
        }
    	if(!freeWorkers.isEmpty()) {
    		if (p.getResources() > barracksType.cost) {
                Unit u = freeWorkers.remove(0);
                int pos = conveBuildPosition(reservedPositions, u, p, pgs);
                build(u, barracksType, pos % pgs.getWidth(), pos / pgs.getWidth());
                reservedPositions.add(pos);
            }
    	}
    	return freeWorkers;
    }
    
    public void trainHeavy(Unit u, Player p, PhysicalGameState pgs) {
    	if (p.getResources() >= heavyType.cost) {
            train(u, heavyType);
        }
    }
    
    public void trainLight(Unit u, Player p, PhysicalGameState pgs) {
    	if (p.getResources() >= lightType.cost) {
            train(u, lightType);
        }
    }

    public void trainRanged(Unit u, Player p, PhysicalGameState pgs) {
    	if (p.getResources() >= rangedType.cost) {
            train(u, rangedType);
        }
    }
    
    public void sendToResource(List<Unit> freeWorkers, Player p, PhysicalGameState pgs) {
//    	List<Unit> freeWorkers = new LinkedList<Unit>();
//    	freeWorkers.addAll(workers);
    	if(freeWorkers.isEmpty()) {
    		return;
    	}
    	for (Unit u : freeWorkers) {
            Unit closestBase = null;
            Unit closestResource = null;
            int closestDistance = 0;
            for (Unit u2 : pgs.getUnits()) {
                if (u2.getType().isResource) {
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    if (closestResource == null || d < closestDistance) {
                        closestResource = u2;
                        closestDistance = d;
                    }
                }
            }
            closestDistance = 0;
            for (Unit u2 : pgs.getUnits()) {
                if (u2.getType().isStockpile) {
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    if (closestBase == null || d < closestDistance) {
                        closestBase = u2;
                        closestDistance = d;
                    }
                }
            }
            if (closestResource != null && closestBase != null) {
                harvest(u, closestResource, closestBase);
            }
        }
    }
    
    public int calBase(Player p, PhysicalGameState pgs) {
    	int nbase = 0;
    	for (Unit u2 : pgs.getUnits()) {
    		if (u2.getType() == baseType
                    && u2.getPlayer() == p.getID()) {
    			nbase++;
            }
    	}
    	return nbase;
    }
    
    public int calBarrack(Player p, PhysicalGameState pgs) {
    	int nbarrack = 0;
    	for (Unit u2 : pgs.getUnits()) {
    		if (u2.getType() == barracksType
                    && u2.getPlayer() == p.getID()) {
    			nbarrack++;
            }
    	}
    	return nbarrack;
    }
    
    public int calWorkerUnit(Player p, PhysicalGameState pgs) {
    	int nworker = 0;
    	for (Unit u2 : pgs.getUnits()) {
    		if (u2.getType() == workerType
                    && u2.getPlayer() == p.getID()) {
    			nworker++;
            }
    	}
    	return nworker;
    }
    
    public int calHeavyUnit(Player p, PhysicalGameState pgs) {
    	int nheavy = 0;
    	for (Unit u2 : pgs.getUnits()) {
    		if (u2.getType() == heavyType
                    && u2.getPlayer() == p.getID()) {
    			nheavy++;
            }
    	}
    	return nheavy;
    }
    
    public int calLightUnit(Player p, PhysicalGameState pgs) {
    	int nlight = 0;
    	for (Unit u2 : pgs.getUnits()) {
    		if (u2.getType() == lightType
                    && u2.getPlayer() == p.getID()) {
    			nlight++;
            }
    	}
    	return nlight;
    }

    public int calRangedUnit(Player p, PhysicalGameState pgs) {
    	int nranged = 0;
    	for (Unit u2 : pgs.getUnits()) {
    		if (u2.getType() == rangedType
                    && u2.getPlayer() == p.getID()) {
    			nranged++;
            }
    	}
    	return nranged;
    }
    
    public List<Unit> calAllUnits(GameState gs, Player p, PhysicalGameState pgs) {
    	List<Unit> armyList = new LinkedList<Unit>();
    	for (Unit u : pgs.getUnits()) {
            if (u.getType().canAttack && u.getType() != workerType && u.getPlayer() == p.getID()) {
                armyList.add(u);
            }
        }
    	return armyList;
    }
    
    public List<Unit> calEnemyArmyUnits(GameState gs, Player p, PhysicalGameState pgs) {
    	List<Unit> armyList = new LinkedList<Unit>();
    	for (Unit u2 : pgs.getUnits()) {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID() && u2.getType() != workerType) {
            	armyList.add(u2);
            }
    	}
    	return armyList;
    }
    
    public List<Unit> calEnemyWorkerUnits(GameState gs, Player p, PhysicalGameState pgs) {
    	List<Unit> armyList = new LinkedList<Unit>();
    	for (Unit u2 : pgs.getUnits()) {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID() && u2.getType() == workerType) {
            	armyList.add(u2);
            }
    	}
    	return armyList;
    }
    
    public List<Unit> calAroundEnemy(List<Unit> armyList, Player p, PhysicalGameState pgs) {
    	Unit aroundEnemy = null;
    	List<Unit> aroundEnemyList = new LinkedList<Unit>();
        for (Unit u : armyList) {
            for (Unit u2 : pgs.getUnits()) {
                if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) {
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    if (aroundEnemy == null || d < closedDist) {
                    	aroundEnemy = u2;
                    	closedDist = d;
                    	aroundEnemyList.add(u2);
                    }
                }
            }
        }
        return aroundEnemyList;
    }
    
    public int workerNum(List<Unit> workers) {
    	return workers.size();
    }
    
    public void sendToAttack(List<Unit> armyList, Player p, PhysicalGameState pgs) {
    	Unit closestEnemy = null;
        int closestDistance = 0;
        for (Unit u : armyList) {
            for (Unit u2 : pgs.getUnits()) {
                if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) {
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    if (closestEnemy == null || d < closestDistance) {
                        closestEnemy = u2;
                        closestDistance = d;
                    }
                }
            }
            if (closestEnemy != null) {
                attack(u, closestEnemy);
            }
        }
    }

    public int findBuildingPosition(List<Integer> reserved, Unit u, Player p, PhysicalGameState pgs) {
        int bestPos = -1;
        int bestScore = 0;

        for (int x = 0; x < pgs.getWidth(); x++) {
            for (int y = 0; y < pgs.getHeight(); y++) {
                int pos = x + y * pgs.getWidth();
                if (!reserved.contains(pos) && pgs.getUnitAt(x, y) == null) {
                    int score = 0;

                    score = -(Math.abs(u.getX() - x) + Math.abs(u.getY() - y));

                    if (bestPos == -1 || score > bestScore) {
                        bestPos = pos;
                        bestScore = score;
                    }
                }
            }
        }

        return bestPos;
    }
    
    public void attackAround(List<Unit> armyList, Player p, PhysicalGameState pgs) {
    	Unit closestEnemy = null;
//        int closestDistance = 0;
        for (Unit u : armyList) {
            for (Unit u2 : pgs.getUnits()) {
                if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) {
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    if (closestEnemy == null || d < closedDist) {
                        closestEnemy = u2;
//                        closestDistance = d;
                    }
                }
            }
            if (closestEnemy != null) {
                attack(u, closestEnemy);
            }
        }
    }
    
    public void sendToDefense(List<Unit> armyList, Player p, PhysicalGameState pgs) {
    	Unit attackingEnemy = null;
    	Unit ub = null;
        int attackingDistance = safeDist;
        int nbase = 0;
        for (Unit u2 : pgs.getUnits()) {
    		if (u2.getType() == baseType && u2.getPlayer() == p.getID()) {
    			ub = u2;
    			nbase++;
            }
    	}       
        for (Unit u : armyList) {
            for (Unit u2 : pgs.getUnits()) {
                if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID() && nbase == 1) {
                    int d = Math.abs(u2.getX() - ub.getX()) + Math.abs(u2.getY() - ub.getY());
                    if (attackingEnemy == null && d < attackingDistance) {
                    	attackingEnemy = u2;
//                    	attackingDistance = d;
                    }
                }
            }
            if (attackingEnemy != null && u.getType().canAttack == true && u.getType() != workerType) {
                attack(u, attackingEnemy);
            }
        }
    }
    
    public int conveBuildPosition(List<Integer> reserved, Unit u, Player p, PhysicalGameState pgs) {
    	int bestPos = -1;
        int width = pgs.getWidth();
        int height = pgs.getHeight();
        if(smallMap == true) {
	        if(upPos == true) {
	        	for(int x = bx - 1; x < width / 2; x = x + 2) {
	        		int y = by + 1;
	        		if(pgs.getUnitAt(x, y) == null) {
	        			bestPos = x + y * pgs.getWidth();
	        			break;
	        		}
	        	}
	        }
	        else {
	        	for(int y = by - 1; y < height; y = y + 2) {
	        		int x = bx - 1;
	        		if(pgs.getUnitAt(x, y) == null) {
	        			bestPos = x + y * pgs.getWidth();
	        			break;
	        		}
	        	}
	        }
        }
        else if(midMap == true) {
        	if(upPos == true) {
	        	for(int x = bx - 1; x < width / 2; x = x + 2) {
	        		int y = by + 1;
	        		if(pgs.getUnitAt(x, y) == null) {
	        			bestPos = x + y * pgs.getWidth();
	        			break;
	        		}
	        	}
	        }
	        else {
	        	for(int y = by + 1; y > height / 2; y = y - 2) {
	        		int x = bx - 2;
	        		if(pgs.getUnitAt(x, y) == null) {
	        			bestPos = x + y * pgs.getWidth();
	        			break;
	        		}
	        	}
	        }
        }
        else if(bigMap == true) {
        	if(upPos == true) {
        		for(int x = bx - 1; x < width / 2; x = x + 2) {
        			int y = by + 3;
	        		if(pgs.getUnitAt(x, y) == null) {
	        			bestPos = x + y * pgs.getWidth();
	        			break;
	        		}
	        	}
	        }
	        else {
	        	for(int y = by + 1; y > height / 2; y = y - 2) {
	        		int x = bx - 2;
	        		if(pgs.getUnitAt(x, y) == null) {
	        			bestPos = x + y * pgs.getWidth();
	        			break;
	        		}
	        	}
	        }
        }
        return bestPos;
    }
    
    public void workersBehavior(List<Unit> workers, Player p, PhysicalGameState pgs) {
        int nbases = 0;
        int nbarracks = 0;

        int resourcesUsed = 0;
        List<Unit> freeWorkers = new LinkedList<Unit>();
        freeWorkers.addAll(workers);

        if (workers.isEmpty()) {
            return;
        }

        for (Unit u2 : pgs.getUnits()) {
            if (u2.getType() == baseType
                    && u2.getPlayer() == p.getID()) {
                nbases++;
            }
            if (u2.getType() == barracksType
                    && u2.getPlayer() == p.getID()) {
                nbarracks++;
            }
        }

        List<Integer> reservedPositions = new LinkedList<Integer>();
        if (nbases == 0 && !freeWorkers.isEmpty()) {
            // build a base:
            if (p.getResources() > baseType.cost + resourcesUsed) {
                Unit u = freeWorkers.remove(0);
                int pos = findBuildingPosition(reservedPositions, u, p, pgs);
                build(u, baseType, pos % pgs.getWidth(), pos / pgs.getWidth());
                resourcesUsed += baseType.cost;
                reservedPositions.add(pos);
            }
        }

        if (nbarracks < 2 && !freeWorkers.isEmpty()) {
            // build a barracks:
            if (p.getResources() > barracksType.cost + resourcesUsed) {
                Unit u = freeWorkers.remove(0);
                int pos = findBuildingPosition(reservedPositions, u, p, pgs);
                build(u, barracksType, pos % pgs.getWidth(), pos / pgs.getWidth());
                resourcesUsed += baseType.cost;
            }
        }


        // harvest with all the free workers:
        for (Unit u : freeWorkers) {
            Unit closestBase = null;
            Unit closestResource = null;
            int closestDistance = 0;
            for (Unit u2 : pgs.getUnits()) {
                if (u2.getType().isResource) {
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    if (closestResource == null || d < closestDistance) {
                        closestResource = u2;
                        closestDistance = d;
                    }
                }
            }
            closestDistance = 0;
            for (Unit u2 : pgs.getUnits()) {
                if (u2.getType().isStockpile) {
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    if (closestBase == null || d < closestDistance) {
                        closestBase = u2;
                        closestDistance = d;
                    }
                }
            }
            if (closestResource != null && closestBase != null) {
                harvest(u, closestResource, closestBase);
            }
        }
    }
}
