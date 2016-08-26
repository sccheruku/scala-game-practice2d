package ui

import javax.swing.JPanel
import ui.elements.Grid
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Color
import java.awt.Dimension
import java.awt.Toolkit
import listeners.GridMouseListener
import java.awt.event.MouseEvent
import models.Entity
import models.Ship
import models.enums.ShipType
import models.enums.EntityType
import java.awt.Font
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.Rectangle2D
import java.awt.Cursor
import utils.fsm.GameStateMachine
import utils.fsm.SelectionStates
import utils.fsm.GameStates
import utils.fsm.SelectionStatesMap
import utils.fsm.SelectionStateActions

class BasicBoard extends JPanel {
  def pWidth: Int = 1280
  def pHeight: Int = 720
  var grid : Grid = null
  var entities : List[Entity] = List[Entity]()
  var selectedEntity : Option[Entity] = None: Option[Entity]
  var cursors : List[(String, Cursor)] = List[(String, Cursor)]()
  var stateMachine: GameStateMachine = new GameStateMachine()
  initBoard()
  def initCursors(){
    cursors = ("CROSSHAIR_CURSOR", new Cursor(Cursor.CROSSHAIR_CURSOR)) :: cursors
    cursors = ("MOVE_CURSOR", new Cursor(Cursor.MOVE_CURSOR)) :: cursors
    cursors = ("HAND_CURSOR", new Cursor(Cursor.HAND_CURSOR)) :: cursors
    setCursor(cursors.find(c => c._1 == "HAND_CURSOR").get._2)
  }
  def initEntities(){
    val initEnemyShips: Int = 3
    val initPlayerShips: Int = 1
    List.range(0,3).foreach(r => {
      val p = Utils.getEmptyLocation(grid.gridCount, this)
      entities = Ship(p.x, p.y, ShipType.random(), EntityType.ENEMY_SHIP) :: entities
    })
    List.range(0,initPlayerShips).foreach(r => {
      val p = Utils.getEmptyLocation(grid.gridCount, this)
      val ps = Ship(p.x, p.y, ShipType.FIGHTER, EntityType.PLAYER_SHIP)
      entities = ps :: entities      
    })
    entities.foreach(e => e.printInfo)
  }
  
  def initBoard(){
    setBackground(Color.BLACK)
    setDoubleBuffered(true)
    println(s"pw: ${pWidth} , ph: ${pHeight}")
    val hw = Math.min(pWidth, pHeight)
    grid = new Grid(hw, hw)
    val gml = new GridMouseListener()
    attachListenerFunctions(gml)
    addMouseListener(gml)
    initEntities()
    initCursors()
  }
  def attachListenerFunctions(gml: GridMouseListener){
    gml.addMouseClickEvent("highlightGridLoc", (e: MouseEvent) => {
      val p = e.getPoint
      if (p.x > grid.maxLineSize || p.y > grid.maxLineSize){
        grid.highlightedGridLoc = null
      }
      else{
        grid.setSelectedGridLoc(p)
      }
      repaint()
    })
    
    gml.addMouseClickEvent("selectShip", (e: MouseEvent) => {
      val movablePositions =  (if (selectedEntity.isDefined) Utils.getMovablePositions(selectedEntity.get.asInstanceOf[Ship], this)
                              else List[Point]())
      val p = e.getPoint
      val x = (p.x - (p.x % grid.gridSize))/grid.gridSize
      val y = (p.y - (p.y % grid.gridSize))/grid.gridSize
      selectedEntity = entities.find(e => e.x == x && e.y == y)
      if (selectedEntity.isDefined){
        setCursor(cursors.find(c => c._1 == "CROSSHAIR_CURSOR").get._2)
        selectedEntity.get.entityType match {
          case EntityType.ENEMY_SHIP => {
            if (movablePositions.size == 0)
              stateMachine.selectionState = SelectionStatesMap.next(stateMachine.selectionState, SelectionStateActions.SELECTED_ENEMY)
          }
          case EntityType.PLAYER_SHIP => { 
            stateMachine.selectionState = SelectionStatesMap.next(stateMachine.selectionState, SelectionStateActions.SELECTED_PLAYER)
          }
        }
        if (stateMachine.selectionState == SelectionStates.NONE){
          selectedEntity  = None: Option[Entity]
          grid.highlightedGridLoc = null
        }
      }
      else if (!selectedEntity.isDefined){
        if (movablePositions.find(mp => mp.x == p.x && mp.y == p.y).isDefined) {
          println("MOVE PLAYER TO POSITION")
          SelectionStates.SELECTED_MOVE_LOCATION
        }
      }
      else {
        setCursor(cursors.find(c => c._1 == "HAND_CURSOR").get._2)
        stateMachine.gameState = GameStates.USER_TURN
        stateMachine.selectionState = SelectionStates.NONE
      }
      
      // ATTACK/MOVE HERE
      if (stateMachine.selectionState == SelectionStates.SELECTED_ATTACK_LOCATION){
        println("SELECTED_ATTACK_LOCATION")
        stateMachine.selectionState = SelectionStates.NONE
        selectedEntity  = None: Option[Entity]
        grid.highlightedGridLoc = null
      }
      else if (stateMachine.selectionState == SelectionStates.SELECTED_MOVE_LOCATION){
        println("SELECTED_MOVE_LOCATION")
        stateMachine.selectionState = SelectionStates.NONE
        selectedEntity  = None: Option[Entity]
        grid.highlightedGridLoc = null
      }
      debugPrintCurrentState()
      repaint() 
    })
  } 
  def debugPrintCurrentState(){
    println(s"gameState: ${stateMachine.gameState} & selectionState: ${stateMachine.selectionState}")
  }
  override def paintComponent(g: Graphics){
    super.paintComponent(g)
    grid.draw(g.asInstanceOf[Graphics2D])
    Toolkit.getDefaultToolkit().sync()
    
    /* draw entities on the board */
    g.setColor(Color.WHITE)
    val f = new Font("Ariel", 1, 12)
    g.setFont(f)
    entities.foreach(e => {
      val x =  (e.x * grid.gridSize) + grid.gridSize/4 - 5
      val y = (e.y * grid.gridSize) + grid.gridSize/4
      if (selectedEntity.isDefined && e == selectedEntity.get) g.setColor(Color.BLACK)
      e.draw(g.asInstanceOf[Graphics2D], new Point(x,y))
      g.setColor(Color.WHITE)
    })
    
    /* Selected entity info */
    if (selectedEntity.isDefined){
      val lines = selectedEntity.get.toString.split("\n")
      lines.zipWithIndex.foreach(f => {
        g.drawString(f._1, grid.width, 15 * (f._2+1))
      })
      /*Draw movable area*/
      val movablePoints = Utils.getMovablePositions(selectedEntity.get.asInstanceOf[Ship], this)
      movablePoints.foreach( p => {
        g.setColor(Color.decode("#AA6600"))
        g.drawRect(p.x * grid.gridSize, p.y * grid.gridSize, grid.gridSize, grid.gridSize)
        g.setColor(Color.decode("#e68a00"))
        g.asInstanceOf[Graphics2D].fill(new Rectangle2D.Double(p.x * grid.gridSize, p.y * grid.gridSize, grid.gridSize, grid.gridSize))
      })
    }
    
    /* Draw random square*/
    g.setColor(Color.orange)
    g.drawString("Color test string", grid.width,100)
    g.setColor(Color.cyan)
    g.drawString("Color test string", grid.width,120)
    g.setColor(Color.yellow)
    g.drawString("Color test string", grid.width,140)
    g.setColor(Color.lightGray)
    g.drawString("Color test string", grid.width,160)
    g.setColor(Color.decode("#AA6600"))
    g.drawString("Color test string", grid.width,180)
    
    g.drawString("with crosshair, click orange to move, click enemy to attack", grid.width, 200)
    g.drawString("with crosshair, show percentage of attack plus possible damage to each enemy", grid.width, 220)
  }
}