*&---------------------------------------------------------------------*
*& Function Module Z_TRANSPORT_LIST
*&---------------------------------------------------------------------*
*& RFC enabled function module that reads the transport management system,
*& so the Change & Transport Management Analyzer can run against a live
*& landscape instead of a spreadsheet somebody keeps by hand.
*&
*& CREATED WITH SE37 -> "Remote-Enabled" MUST BE TICKED.
*& Without that flag the function module is not registered for RFC and the
*& call fails with "not found / not remote enabled" - which appears in
*& SM58 on the calling system, not as a dump here.
*&
*& Source: E070 - the header table of every transport request, holding the
*& type, the status, the owner, the creation date and the target system.
*& The object list of a request comes from TRDIR, the transport directory,
*& which holds one entry per object (and, for customizing, per object and
*& field). E070LIN is the link table between the two.
*&
*& A note on honesty: the exact field names of these tables differ between
*& releases and between the classic and the new status model, and the
*& per-object change timestamp is not held in the transport tables at all -
*& it comes from the change log (CDHDR / CDCLS). This module therefore
*& returns the request date as the fallback for the object date, and a real
*& implementation reads the change log for the exact value. The interface
*& itself does not change, only the ABAP behind it.
*&
*& What this function module deliberately does NOT do: it does not decide
*& whether a request may be imported. That is the analyzer's job - the
*& database knows what was transported, and only the analyzer knows what
*& conflicts with what.
*&---------------------------------------------------------------------*

FUNCTION z_transport_list.
*"*" Local Interface:
*"*" IMPORTING
*"*" VALUE(iv_request) TYPE string OPTIONAL
*"*" EXPORTING
*"*" VALUE(ev_count) TYPE i
*"*" VALUE(ev_message) TYPE string
*"*" TABLES
*"*" it_requests TYPE ztr_req_tab
*"*" it_objects  TYPE ztr_obj_tab

  DATA: lv_count TYPE i.

  CLEAR: ev_count, ev_message, it_requests, it_objects.

  " -- the request headers ------------------------------------------------
  SELECT trkorr trfunction trstatus truser trdate target as4text as4user parent_trkorr
    FROM e070
    WHERE ( @iv_request IS INITIAL OR trkorr = @iv_request )
    ORDER BY trdate DESCENDING
    INTO TABLE @DATA(lt_requests).

  LOOP AT lt_requests INTO DATA(ls_request).
    lv_count = lv_count + 1.

    " The transport system stores the type as a single character.
    DATA(lv_type) = COND string(
      WHEN ls_request-trfunction = 'W' THEN 'WORKBENCH'
      WHEN ls_request-trfunction = 'C' THEN 'CUSTOMIZING'
      WHEN ls_request-trfunction = 'T' THEN 'TASK'
      WHEN ls_request-trfunction = 'K' THEN 'TASK_CONTAINER'
      ELSE 'WORKBENCH' ).

    " And the status as another one. The readable text comes from the
    " status table when the release provides it.
    DATA(lv_status) = COND string(
      WHEN ls_request-trstatus = 'D' THEN 'D'
      WHEN ls_request-trstatus = 'R' THEN 'R'
      WHEN ls_request-trstatus = 'L' THEN 'L'
      WHEN ls_request-trstatus = 'O' THEN 'O'
      ELSE ls_request-trstatus ).

    DATA(lv_status_text) = COND string(
      WHEN ls_request-trstatus = 'D' THEN 'In development'
      WHEN ls_request-trstatus = 'R' THEN 'Released for transport'
      WHEN ls_request-trstatus = 'L' THEN 'Released for transport'
      WHEN ls_request-trstatus = 'O' THEN 'Imported'
      ELSE 'Unknown' ).

    APPEND VALUE #(
      trkorr         = ls_request-trkorr
      description    = ls_request-as4text
      req_type       = lv_type
      trstatus       = lv_status
      status_text    = lv_status_text
      truser         = ls_request-as4user
      trdate         = |{ ls_request-trdate DATE = TIME }|
      target_system  = ls_request-target
      source_system  = COND #( WHEN ls_request-trfunction = 'W'
                                THEN ls_request-parent_trkorr
                                ELSE '' )
      parent_request = ls_request-parent_trkorr
    ) TO it_requests.
  ENDLOOP.

  " -- the objects of those requests ---------------------------------------
  IF lt_requests IS NOT INITIAL.
    SELECT trkorr name type field pgmid
      FROM trdir
      WHERE trkorr IN @lt_requests[ trkorr ]
      ORDER BY type name
      INTO TABLE @DATA(lt_objects).

    LOOP AT lt_objects INTO DATA(ls_object).
      " The conflict key is what decides whether two requests collide:
      " for a workbench object it is the name and the type, for a
      " customizing entry it is the name and the field.
      DATA(lv_key) = COND string(
        WHEN ls_object-field IS INITIAL OR ls_object-field IS NOT INITIAL
          THEN |{ ls_object-name } ({ ls_object-type })|
        ELSE |{ ls_object-name }| ).

      IF ls_object-field IS NOT INITIAL.
        lv_key = |{ ls_object-name }.{ ls_object-field }|.
      ENDIF.

      APPEND VALUE #(
        obj_id       = |{ ls_object-trkorr }-{ ls_object-name }|
        trkorr       = ls_object-trkorr
        pgmid        = ls_object-pgmid
        obj_type     = ls_object-type
        obj_name     = ls_object-name
        obj_key      = lv_key
        field_name   = ls_object-field
        last_changed = |{ ls_object-trkorr }|
      ) TO it_objects.
    ENDLOOP.
  ENDIF.

  ev_count = lv_count.

  IF ev_count = 0.
    ev_message = 'No transport requests found.'.
  ELSE.
    ev_message = |{ ev_count } request(s) returned.|.
  ENDIF.

ENDFUNCTION.
