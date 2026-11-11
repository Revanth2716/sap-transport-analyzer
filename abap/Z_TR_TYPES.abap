*&---------------------------------------------------------------------*
*& DDIC types for the Transport & Change Management RFC interface
*&---------------------------------------------------------------------*
*& Create these in SE11. Flat structures, so they can be used as RFC
*& table parameters without any conversion rules.
*&---------------------------------------------------------------------*

"! <p class="text">One transport request - the shape the analyzer expects
"! from a live system.</p>
TYPES : BEGIN OF ztr_req,
         trkorr         TYPE string,   " Request number
         description    TYPE string,   " Short text
         req_type       TYPE string,   " WORKBENCH / CUSTOMIZING / TASK / TASK_CONTAINER
         trstatus       TYPE string,   " D / R / L / O
         status_text    TYPE string,   " Readable status
         truser         TYPE string,   " Owner
         trdate         TYPE string,   " Created, YYYYMMDDHHMMSS
         target_system  TYPE string,   " Where it goes
         source_system  TYPE string,   " Where it came from
         parent_request TYPE string,   " Workbench request of a task
       END OF ztr_req.

TYPES ztr_req_tab TYPE STANDARD TABLE OF ztr_req.

"! <p class="text">One object inside a transport request.</p>
TYPES : BEGIN OF ztr_obj,
         obj_id      TYPE string,   " Stable identifier
         trkorr      TYPE string,   " The request it belongs to
         pgmid       TYPE string,   " R / WS / C / S / T
         obj_type    TYPE string,   " PROG / TABL / CLAS / ENQU / XSLT ...
         obj_name    TYPE string,   " Object name
         obj_key     TYPE string,   " Conflict key: name + type, or name + field
         field_name  TYPE string,   " The customizing field, if any
         last_changed TYPE string,  " Changed, YYYYMMDDHHMMSS
       END OF ztr_obj.

TYPES ztr_obj_tab TYPE STANDARD TABLE OF ztr_obj.
